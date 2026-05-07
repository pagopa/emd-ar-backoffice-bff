package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.model.User;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles Keycloak user provisioning: create-or-update (upsert) and federated identity linking.
 */
@Service
@Slf4j
public class KeycloakUserService extends AbstractKeycloakService {

    private final WebClient webClient;
    private final String idpAlias;

    public KeycloakUserService(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.idp-alias}") String idpAlias) {
        super(authServerUrl, realm, objectMapper);
        this.webClient = webClient;
        this.idpAlias = idpAlias;
    }

    /**
     * Creates or updates a Keycloak user from the provided user data, then ensures the federated
     * identity link to the external IdP is established.
     *
     * @param managerToken Keycloak admin token with user-management permissions
     * @param userDTO      user info extracted from the incoming JWT
     * @return {@code Mono<Void>} completing when all operations succeed
     */
    public Mono<Void> upsertKeycloakUser(String managerToken, User userDTO) {
        log.info("[AR-BFF][UPSERT_USER] Start");
        log.debug("[AR-BFF][UPSERT_USER] uid={}", userDTO.getUid());

        return getKeycloakUser(userDTO.getUid(), managerToken)
                .flatMap(users -> {
                    Map<String, Object> payload = buildKeycloakUserPayload(userDTO);
                    if (users.isEmpty()) {
                        log.info("[AR-BFF][UPSERT_USER] User not found, creating");
                        return createKeycloakUser(managerToken, payload)
                                .doOnSuccess(id -> log.debug("[AR-BFF][UPSERT_USER] User created, kc_id={}", id))
                                .flatMap(newId -> linkFederatedIdentityToUser(managerToken, newId, userDTO.getUid()));
                    } else {
                        String internalId = (String) users.getFirst().get("id");
                        log.info("[AR-BFF][UPSERT_USER] User found, updating");
                        log.debug("[AR-BFF][UPSERT_USER] kc_id={}", internalId);
                        return updateKeycloakUser(managerToken, internalId, payload)
                                .then(linkFederatedIdentityToUser(managerToken, internalId, userDTO.getUid()));
                    }
                })
                .doOnSuccess(v -> log.info("[AR-BFF][UPSERT_USER] Completed successfully"))
                .doOnError(e -> log.error("[AR-BFF][UPSERT_USER] Failed: {}", e.getMessage()));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Mono<List<Map<String, Object>>> getKeycloakUser(String username, String adminToken) {
        log.info("[AR-BFF][GET_KC_USER] Searching for user");
        log.debug("[AR-BFF][GET_KC_USER] username={}", username);

        URI uri = UriComponentsBuilder.fromUriString(authServerUrl)
                .path("/admin/realms/{realm}/users")
                .queryParam("username", username)
                .queryParam("exact", true)
                .buildAndExpand(realm)
                .toUri();

        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("getKeycloakUser", body)))
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork());
    }

    private Mono<String> createKeycloakUser(String adminToken, Map<String, Object> payload) {
        log.info("[AR-BFF][CREATE_USER] Sending POST to Keycloak");
        return webClient.post()
                .uri(adminUri("/users"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("createKeycloakUser", body)))
                .toBodilessEntity()
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .map(response -> {
                    String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                    if (location == null) {
                        throw new ExternalServiceException("KEYCLOAK", "createKeycloakUser",
                                "Location header missing in response");
                    }
                    String newId = location.substring(location.lastIndexOf('/') + 1);
                    log.debug("[AR-BFF][CREATE_USER] kc_id={}", newId);
                    return newId;
                });
    }

    private Mono<Void> updateKeycloakUser(String adminToken, String internalId, Map<String, Object> payload) {
        return webClient.put()
                .uri(adminUri("/users/{userId}", internalId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("updateKeycloakUser", body)))
                .toBodilessEntity()
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .then();
    }

    private Map<String, Object> buildKeycloakUserPayload(User userDTO) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", userDTO.getUid());
        body.put("email", userDTO.getEmail());
        body.put("firstName", userDTO.getName());
        body.put("lastName", userDTO.getFamilyName());
        body.put("enabled", true);
        body.put("emailVerified", true);

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("org_id", List.of(userDTO.getOrganization().getId()));

        if (userDTO.getOrganization().getRoles() != null && !userDTO.getOrganization().getRoles().isEmpty()) {
                    String role = userDTO.getOrganization().getRoles().getFirst().getRole();
            attributes.put("org_role", List.of(role));
        }

        body.put("attributes", attributes);
        return body;
    }

    private Mono<Void> linkFederatedIdentityToUser(String adminToken, String internalUserId, String externalUserId) {
        log.info("[AR-BFF][LINK_IDENTITY] Linking to IdP: idp={}", idpAlias);
        log.debug("[AR-BFF][LINK_IDENTITY] uid={} kc_id={}", externalUserId, internalUserId);

        Map<String, String> payload = Map.of(
                "userId", externalUserId,
                "userName", externalUserId);

        return webClient.post()
                .uri(adminUri("/users/{userId}/federated-identity/{idpAlias}", internalUserId, idpAlias))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("linkFederatedIdentity", body)))
                .toBodilessEntity()
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .doOnSuccess(r -> log.info("[AR-BFF][LINK_IDENTITY] Identity linked successfully"))
                // 409 Conflict = already linked → idempotent, skip gracefully
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.CONFLICT) {
                        log.info("[AR-BFF][LINK_IDENTITY] Already linked (409), skipping");
                        return Mono.empty();
                    }
                    log.error("[AR-BFF][LINK_IDENTITY] Failed: {}", e.getResponseBodyAsString());
                    return Mono.error(new ExternalServiceException("KEYCLOAK", "linkFederatedIdentity",
                            e.getResponseBodyAsString()));
                })
                .then();
    }
}

