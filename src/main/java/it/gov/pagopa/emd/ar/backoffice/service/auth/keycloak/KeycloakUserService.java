package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakUserDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.PageResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.model.Organization;
import it.gov.pagopa.emd.ar.backoffice.domain.model.Role;
import it.gov.pagopa.emd.ar.backoffice.domain.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
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
    private final String idpAliasMdc;
    private static final String USER_ENDPOINT = "/admin/realms/{realm}/users";
    private static final String SEARCH_FIELD = "search";
    private static final String IDP_ALIAS_FIELD = "idpAlias";

    public KeycloakUserService(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.idp-alias}") String idpAlias,
            @Value("${keycloak.idp-alias-mdc}") String idpAliasMdc) {
        super(authServerUrl, realm, objectMapper);
        this.webClient = webClient;
        this.idpAlias = idpAlias;
        this.idpAliasMdc = idpAliasMdc;
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

        return getKeycloakUser(userDTO.getEmail(), managerToken)
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

    private Mono<List<Map<String, Object>>> getKeycloakUser(String email, String adminToken) {
        log.info("[AR-BFF][GET_KC_USER] Searching for user");
        log.debug("[AR-BFF][GET_KC_USER] email={}", email);

        URI uri = UriComponentsBuilder.fromUriString(authServerUrl)
                .path(USER_ENDPOINT)
                .queryParam("username", email)
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
        body.put("username", userDTO.getEmail());
        body.put("email", userDTO.getEmail());
        body.put("firstName", userDTO.getName());
        body.put("lastName", userDTO.getFamilyName());
        body.put("enabled", true);
        body.put("emailVerified", true);

        Organization org = userDTO.getOrganization();
        Map<String, List<String>> attributes = new HashMap<>();

        // selfcareUserId — maps the external uid as Keycloak attribute
        attributes.put("selfcareUserId", List.of(userDTO.getUid()));

        // orgId — single-value attribute
        if (org.getId() != null) {
            attributes.put("orgId", List.of(org.getId()));
        }

        // orgRoles — multi-valued: collect all role values from the list
        if (org.getRoles() != null && !org.getRoles().isEmpty()) {
            List<String> roles = org.getRoles().stream()
                    .map(Role::getRole)
                    .filter(r -> r != null && !r.isBlank())
                    .toList();
            if (!roles.isEmpty()) {
                attributes.put("orgRoles", roles);
            }
        }

        // orgFiscalCode — single-value attribute
        if (org.getFiscalCode() != null) {
            attributes.put("orgFiscalCode", List.of(org.getFiscalCode()));
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
                // 409 = already linked: consume body silently, emit NO error (overrides the default 4xx handler)
                .onStatus(status -> status.value() == 409,
                        response -> response.bodyToMono(String.class)
                                .doOnNext(body -> log.info("[AR-BFF][LINK_IDENTITY] Already linked (409), skipping"))
                                .then(Mono.<Throwable>empty()))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("linkFederatedIdentity", body)))
                .toBodilessEntity()
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .doOnSuccess(r -> log.info("[AR-BFF][LINK_IDENTITY] Identity linked successfully"))
                .then();
    }

    /**
     * Retrieves a list of users filtered by the MDC IdP alias, and enriches each user 
     * with their respective realm and client roles.
     * 
     * @param adminToken Keycloak admin token
     * @param search     optional search string
     * @param first      pagination offset
     * @param max        pagination limit
     * @return {@code Mono<List<KeycloakUserDTOV1>>} a list of users enriched with roles
     */
    public Mono<List<KeycloakUserDTOV1>> getUsersWithRoles(String adminToken, String search, Integer first, Integer max) {
        log.info("[AR-BFF][GET_USERS] Fetching users. search={}, idpAlias={}, first={}, max={}", search, idpAlias, first, max);
        return Mono.fromCallable(() -> {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerUrl).path(USER_ENDPOINT);
            if (search != null && !search.isBlank()) {
                builder.queryParam(SEARCH_FIELD, search);
            }
            if (first != null) {
                builder.queryParam("first", first);
            }
            if (max != null) {
                builder.queryParam("max", max);
            }
            // Filter to exclude organization users and fetch only operators
            builder.queryParam(IDP_ALIAS_FIELD, idpAliasMdc);
            return builder.buildAndExpand(realm).toUri();
        }).flatMap(uri -> webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("getUsers", body)))
                .bodyToMono(new ParameterizedTypeReference<List<KeycloakUserDTOV1>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                
                // Transforms the list into a stream of single users
                .flatMapMany(Flux::fromIterable)
                // concurrency=10: Launches max 10 parallel calls to avoid stressing Keycloak
                .flatMap(user -> populateUserWithRoles(adminToken, user), 10)
                 // Reassembles the stream into a single final List
                .collectList()
        );
    }

    /**
     * Helper method: calls the role-mappings API for a single user and enriches the user object.
     * 
     * @param adminToken Keycloak admin token
     * @param user       the Keycloak user object to be enriched
     * @return {@code Mono<KeycloakUserDTOV1>} the user containing realm and client roles
     */
    private Mono<KeycloakUserDTOV1> populateUserWithRoles(String adminToken, KeycloakUserDTOV1 user) {
        return webClient.get()
                .uri(adminUri("/users/{id}/role-mappings", user.getId()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    List<String> realmRoles = new ArrayList<>();
                    Map<String, List<String>> clientRolesMap = new HashMap<>();
                    if (response.containsKey("realmMappings")) {
                        List<Map<String, Object>> realmMappings = (List<Map<String, Object>>) response.get("realmMappings");
                        realmRoles = realmMappings.stream()
                                .map(role -> (String) role.get("name"))
                                .toList();
                    }
                    if (response.containsKey("clientMappings")) {
                        Map<String, Map<String, Object>> clientMappings = (Map<String, Map<String, Object>>) response.get("clientMappings");
                        clientMappings.forEach((clientName, clientData) -> {
                            if (clientData.containsKey("mappings")) {
                                List<Map<String, Object>> mappings = (List<Map<String, Object>>) clientData.get("mappings");
                                List<String> roles = mappings.stream()
                                        .map(role -> (String) role.get("name"))
                                        .toList();
                                clientRolesMap.put(clientName, roles);
                            }
                        });
                    }
                    user.setRealmRoles(realmRoles);
                    user.setClientRoles(clientRolesMap);
                    return user;
                })
                .onErrorResume(e -> {
                    // If fetching roles for 1 user fails, do not block the entire list.
                    // Return the user with empty role lists and log the error.
                    log.warn("[AR-BFF] Failed to fetch roles for user {}: {}", user.getId(), e.getMessage());
                    user.setRealmRoles(List.of());
                    user.setClientRoles(Map.of());
                    return Mono.just(user);
                });
    }

    /**
     * Retrieves the total count of users matching the search criteria.
     * 
     * @param adminToken Keycloak admin token
     * @param search     optional search string
     * @return {@code Mono<Integer>} the total number of users
     */
    private Mono<Integer> countUsers(String adminToken, String search) {
        log.info("[AR-BFF][COUNT_USERS] Fetching total count. search={}, idpAlias={}", search, idpAliasMdc);
        return Mono.fromCallable(() -> {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerUrl)
                    .path(USER_ENDPOINT + "/count");
            if (search != null && !search.isBlank()) {
                builder.queryParam(SEARCH_FIELD, search);
            }
            builder.queryParam(IDP_ALIAS_FIELD, idpAliasMdc);
            return builder.buildAndExpand(realm).toUri();
        }).flatMap(uri -> webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .bodyToMono(Integer.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
        );
    }

    /**
     * Retrieves a paginated list of users enriched with their roles, alongside the total user count.
     * Executes the list fetching and the counting in parallel using {@code Mono.zip}.
     * 
     * @param adminToken Keycloak admin token
     * @param search     optional search string
     * @param first      pagination offset
     * @param max        pagination limit
     * @return {@code Mono<PageResponseDTOV1>} the paginated response containing users and total count
     */
    public Mono<PageResponseDTOV1> getPaginatedUsersWithRoles(String adminToken, String search, Integer first, Integer max) {
        
        Mono<List<KeycloakUserDTOV1>> usersListMono = Mono.fromCallable(() -> {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerUrl)
                    .path(USER_ENDPOINT);
            if (search != null && !search.isBlank()) {
                builder.queryParam(SEARCH_FIELD, search);
            }
            if (first != null) {
                builder.queryParam("first", first);
            }
            if (max != null) {
                builder.queryParam("max", max);
            }
            builder.queryParam(IDP_ALIAS_FIELD, idpAliasMdc);
            return builder.buildAndExpand(realm).toUri();
        }).flatMap(uri -> webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<KeycloakUserDTOV1>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .flatMapMany(Flux::fromIterable)
                .flatMap(user -> populateUserWithRoles(adminToken, user), 10)
                .collectList()
        );

        Mono<Integer> countMono = countUsers(adminToken, search);

        // Execute both calls in parallel using Mono.zip
        return Mono.zip(usersListMono, countMono)
                .map(tuple -> PageResponseDTOV1.builder()
                        .content(tuple.getT1())
                        .totalElements(tuple.getT2())
                        .first(first)
                        .max(max)
                        .build());
    }

}
