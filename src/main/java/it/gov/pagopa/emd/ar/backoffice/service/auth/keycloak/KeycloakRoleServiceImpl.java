package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class KeycloakRoleServiceImpl extends AbstractKeycloakService implements KeycloakRoleService {

    private final WebClient webClient;
    private final KeycloakTokenService tokenService;
    private final String arBackofficeClientId;
    
    /** 
     * In-memory cache for the internal Keycloak UUID of the target client
     * to prevent redundant API calls during role operations.
     */
    private final AtomicReference<String> cachedInternalClientId = new AtomicReference<>();

    public KeycloakRoleServiceImpl(WebClient webClient, ObjectMapper objectMapper, KeycloakTokenService tokenService,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
                        @Value("${keycloak.portal-client-id:ar-backoffice-portal-client}") String arBackofficeClientId) { 
        super(authServerUrl, realm, objectMapper);
        this.webClient = webClient;
        this.tokenService = tokenService;
        this.arBackofficeClientId = arBackofficeClientId;
    }

    @Override
    public Mono<List<KeycloakRoleDTOV1>> getAvailableClientRoles() {
        log.info("[AR-BFF][GET_ROLES] Fetching available roles for client: {}", arBackofficeClientId);
        return tokenService.getManagerToken()
                .flatMap(adminToken -> getTargetInternalClientId(adminToken)
                        .flatMap(internalId -> fetchClientRoles(adminToken, internalId)));
    }

    @Override
    public Mono<Void> replaceUserRoles(String userId, List<KeycloakRoleDTOV1> requestedRoles) {
        log.info("[AR-BFF][REPLACE_ROLES] Wiping and replacing roles for user {} on client {}", userId, arBackofficeClientId);
        
        return tokenService.getManagerToken()
                .flatMap(adminToken -> getTargetInternalClientId(adminToken)
                        .flatMap(internalId -> 
                                getUserClientRoles(adminToken, userId, internalId)
                                .flatMap(currentRoles -> {
                                    
                                    Mono<Void> deleteMono = currentRoles.isEmpty() 
                                            ? Mono.empty() 
                                            : deleteRoleMapping(adminToken, userId, internalId, currentRoles);

                                    Mono<Void> addMono = requestedRoles.isEmpty() 
                                            ? Mono.empty() 
                                            : postRoleMapping(adminToken, userId, internalId, requestedRoles);

                                    return deleteMono.then(addMono);
                                })
                        ));
    }

    // ── Metodi Privati ───────────────────────────────────────────────────────
    /**
     * Resolves the textual {@code clientId} into Keycloak's internal UUID.
     * Uses the in-memory cache if the ID was already resolved.
     */
    private Mono<String> getTargetInternalClientId(String adminToken) {
        String cached = cachedInternalClientId.get();
        if (cached != null) {
            return Mono.just(cached);
        }

        URI uri = UriComponentsBuilder.fromUriString(authServerUrl)
                .path("/admin/realms/{realm}/clients")
                .queryParam("clientId", arBackofficeClientId)
                .buildAndExpand(realm)
                .toUri();

        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("findTargetClientInternalId", body)))
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .flatMap(clients -> clients.stream()
                        .map(c -> (String) c.get("id"))
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ExternalServiceException("KEYCLOAK", "resolveTargetClient",
                                "Target client not found in Keycloak: " + arBackofficeClientId))))
                .doOnSuccess(cachedInternalClientId::set);
    }

    /**
     * Fetches all defined roles for the given internal client UUID.
     */
    private Mono<List<KeycloakRoleDTOV1>> fetchClientRoles(String adminToken, String internalClientId) {
        return webClient.get()
                .uri(adminUri("/clients/{id}/roles", internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("fetchClientRoles", body)))
                .bodyToMono(new ParameterizedTypeReference<List<KeycloakRoleDTOV1>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork());
    }

    /**
     * Adds the specified list of roles to the user's role mapping via POST.
     */
    private Mono<Void> postRoleMapping(String adminToken, String userId, String internalClientId, List<KeycloakRoleDTOV1> rolesToAdd) {
        log.info("[AR-BFF][REPLACE_ROLES] Executing POST to add {} new roles on user {}", rolesToAdd.size(), userId);
        return webClient.post()
                .uri(adminUri("/users/{userId}/role-mappings/clients/{clientInternalId}", userId, internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(rolesToAdd)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("assignClientRoles", body)))
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("[AR-BFF][REPLACE_ROLES] New roles successfully added."))
                .then();
    }

    /**
     * Fetches the current roles assigned to the user for the specific client.
     */
    private Mono<List<KeycloakRoleDTOV1>> getUserClientRoles(String adminToken, String userId, String internalClientId) {
        return webClient.get()
                .uri(adminUri("/users/{userId}/role-mappings/clients/{clientInternalId}", userId, internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("getUserClientRoles", body)))
                .bodyToMono(new ParameterizedTypeReference<List<KeycloakRoleDTOV1>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork());
    }

    /**
     * Removes the specified list of roles from the user's role mapping via DELETE.
     * Note: WebClient requires using {@code HttpMethod.DELETE} explicitly to allow a request body.
     */
    private Mono<Void> deleteRoleMapping(String adminToken, String userId, String internalClientId, List<KeycloakRoleDTOV1> rolesToDelete) {
        log.info("[AR-BFF][REPLACE_ROLES] Executing DELETE for {} roles on user {}", rolesToDelete.size(), userId);
        return webClient.method(HttpMethod.DELETE)
                .uri(adminUri("/users/{userId}/role-mappings/clients/{clientInternalId}", userId, internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(rolesToDelete)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("deleteClientRoles", body)))
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("[AR-BFF][REPLACE_ROLES] Old roles successfully removed."))
                .then();
    }

}