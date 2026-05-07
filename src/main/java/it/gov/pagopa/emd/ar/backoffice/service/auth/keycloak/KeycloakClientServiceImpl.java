package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Creates Keycloak OIDC clients for TPP onboarding and associates them with the TPP group.
 *
 * <p>Client creation is <strong>idempotent</strong>: if a 409 Conflict is returned (the
 * client already exists — e.g., after a partial failure and retry), the method looks up
 * the existing client by {@code clientId} and proceeds with the group-linking step.
 * This makes the whole TPP creation flow safe to retry without leaving duplicate clients.
 *
 * <p>The TPP group ID is resolved once and cached for the lifetime of the application,
 * since the group name is static configuration.
 */
@Service
@Slf4j
public class KeycloakClientServiceImpl extends AbstractKeycloakService implements KeycloakClientService {

    private final WebClient webClient;
    private final KeycloakTokenService tokenService;
    private final String tppGroupName;

    /** Lazy cache for the TPP group UUID — resolved once, never changes at runtime. */
    private final AtomicReference<String> cachedTppGroupId = new AtomicReference<>();

    public KeycloakClientServiceImpl(
            WebClient webClient,
            ObjectMapper objectMapper,
            KeycloakTokenService tokenService,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.groups.tpp-name}") String tppGroupName) {
        super(authServerUrl, realm, objectMapper);
        this.webClient = webClient;
        this.tokenService = tokenService;
        this.tppGroupName = tppGroupName;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<String> createKeycloakClient(String clientId) {
        log.info("[AR-BFF][CREATE_CLIENT] Starting process for clientId={}", clientId);

        return tokenService.getManagerToken()
                .flatMap(adminToken -> resolveInternalClientId(adminToken, clientId)
                        .flatMap(internalId -> linkClientToGroup(adminToken, internalId, clientId)))
                .doOnSuccess(res -> log.info("[AR-BFF][CREATE_CLIENT] Successfully created client: {}", clientId))
                .doOnError(e -> log.error("[AR-BFF][CREATE_CLIENT] Failed: {}", e.getMessage()));
    }

    /**
     * Creates the Keycloak client and returns its internal UUID.
     * <p>
     * If Keycloak returns 409 Conflict (the client already exists — e.g., after a partial
     * failure followed by a retry), the method transparently looks up and returns the
     * existing client's internal UUID, making the whole operation idempotent.
     */
    private Mono<String> resolveInternalClientId(String adminToken, String clientId) {
        return webClient.post()
                .uri(adminUri("/clients"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildClientPayload(clientId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("createClient", body)))
                .toBodilessEntity()
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .flatMap(response -> {
                    String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                    if (location == null) {
                        return Mono.error(new ExternalServiceException("KEYCLOAK", "createClient",
                                "Location header missing in response"));
                    }
                    String internalId = location.substring(location.lastIndexOf('/') + 1);
                    log.debug("[AR-BFF][CREATE_CLIENT] Client created, internalId={}", internalId);
                    return Mono.just(internalId);
                })
                // 409: client already exists — idempotency path
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                        log.warn("[AR-BFF][CREATE_CLIENT] 409 for clientId={} — resolving existing internal ID", clientId);
                        return findClientInternalId(adminToken, clientId);
                    }
                    return Mono.error(ex);
                });
    }

    /**
     * Looks up an existing Keycloak client by its {@code clientId} string and returns its internal UUID.
     */
    private Mono<String> findClientInternalId(String adminToken, String clientId) {
        URI uri = UriComponentsBuilder.fromUriString(authServerUrl)
                .path("/admin/realms/{realm}/clients")
                .queryParam("clientId", clientId)
                .queryParam("exact", true)
                .buildAndExpand(realm)
                .toUri();

        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("findClientByClientId", body)))
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .flatMap(clients -> clients.stream()
                        .map(c -> (String) c.get("id"))
                        .filter(id -> id != null)
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("Keycloak client", clientId))));
    }

    /**
     * Resolves the TPP group ID and the client's service-account user ID in parallel,
     * then adds the service-account to the TPP group.
     */
    private Mono<String> linkClientToGroup(String adminToken, String internalClientId, String clientId) {
        return Mono.zip(
                        getTppGroupId(adminToken),
                        getServiceAccountUserId(adminToken, internalClientId))
                .flatMap(tuple -> addUserToGroup(adminToken, tuple.getT2(), tuple.getT1()))
                .thenReturn(clientId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Map<String, Object> buildClientPayload(String clientId) {
        Map<String, Object> client = new HashMap<>();
        client.put("clientId", clientId);
        client.put("name", clientId);
        client.put("enabled", true);
        client.put("protocol", "openid-connect");
        client.put("standardFlowEnabled", false);
        client.put("directAccessGrantsEnabled", false);
        client.put("webOrigins", List.of());
        client.put("bearerOnly", false);
        client.put("publicClient", false);
        client.put("serviceAccountsEnabled", true);
        client.put("redirectUris", List.of());
        client.put("attributes", Map.of("use.refresh.tokens", "true"));
        return client;
    }

    /**
     * Resolves the TPP group UUID, using the in-memory cache after the first call.
     */
    private Mono<String> getTppGroupId(String adminToken) {
        String cached = cachedTppGroupId.get();
        if (cached != null) {
            return Mono.just(cached);
        }
        return resolveGroupIdByName(adminToken, tppGroupName)
                .doOnSuccess(cachedTppGroupId::set);
    }

    private Mono<String> resolveGroupIdByName(String adminToken, String groupName) {
        log.debug("[AR-BFF][GET_GROUP_ID] Searching for group: {}", groupName);

        URI uri = UriComponentsBuilder.fromUriString(authServerUrl)
                .path("/admin/realms/{realm}/groups")
                .queryParam("search", groupName)
                .buildAndExpand(realm)
                .toUri();

        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("getGroupByName", body)))
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .flatMap(groups -> groups.stream()
                        .filter(g -> groupName.equals(g.get("name")))
                        .map(g -> (String) g.get("id"))
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResourceNotFoundException("Keycloak group", groupName))));
    }

    private Mono<String> getServiceAccountUserId(String adminToken, String internalClientId) {
        return webClient.get()
                .uri(adminUri("/clients/{clientId}/service-account-user", internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("getServiceAccountUser", body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .map(user -> (String) user.get("id"));
    }

    private Mono<Void> addUserToGroup(String adminToken, String userId, String groupId) {
        return webClient.put()
                .uri(adminUri("/users/{userId}/groups/{groupId}", userId, groupId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("addUserToGroup", body)))
                .toBodilessEntity()
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .then();
    }
}

