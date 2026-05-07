package it.gov.pagopa.emd.ar.backoffice.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.http.MediaType;

import static it.gov.pagopa.emd.ar.backoffice.constants.BackofficeConstants.ExceptionCode.KEYCLOAK_CLIENT_ALREADY_EXISTS;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.OrganizationDTOV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.UserDTOV1;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.manager.client-id}")
    private String managerClientId;

    @Value("${keycloak.manager.client-secret}")
    private String managerClientSecret;

    @Value("${keycloak.ar-backoffice.client-id}")
    private String backofficeClientId;

    @Value("${keycloak.ar-backoffice.client-secret}")
    private String backofficeClientSecret;

    @Value("${keycloak.idp-alias}")
    private String idpAlias;

    @Value("${keycloak.groups.tpp-name}")
    private String tppGroupName;


    private final SelfCareTokenValidator selfCareValidator;
    private WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * In-memory cache for the Keycloak manager token (client_credentials grant).
     * Avoids a round-trip to Keycloak on every auth request.
     * The token is proactively refreshed before expiry via a background task.
     */
    private record CachedToken(String value, Instant expiresAt) {
        boolean isValid() { return Instant.now().isBefore(expiresAt); }
    }
    private final AtomicReference<CachedToken> managerTokenCache = new AtomicReference<>();

    /**
     * Tracks the currently scheduled proactive refresh task.
     * Ensures only one refresh chain is active at any time — prevents leaks
     * in case of concurrent cold-start fetches.
     */
    private final AtomicReference<Disposable> scheduledRefresh = new AtomicReference<>();

    public AuthServiceImpl(WebClient webClient, SelfCareTokenValidator selfCareValidator, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.selfCareValidator = selfCareValidator;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("[AR-BFF][AUTH_SERVICE] Initialized: auth-server-url={} realm={} idp-alias={}", authServerUrl, realm, idpAlias);
        // Warmup: fetch the manager token eagerly so the first real request finds a warm cache.
        // Uses subscribe() (not block()) to avoid blocking Spring startup.
        // If Keycloak is temporarily unavailable, the first request will trigger a sync fetch as fallback.
        fetchAndCacheManagerToken()
            .subscribe(
                t  -> log.info("[AR-BFF][AUTH_SERVICE] Warmup: manager token ready"),
                e  -> log.warn("[AR-BFF][AUTH_SERVICE] Warmup: failed to pre-fetch manager token, will retry on first request: {}", e.getMessage())
            );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ResponseEntity<AuthResponseV1>> exchangeToken(String token) {
        log.info("[AR-BFF][EXCHANGE_TOKEN] Start");

        if (token == null || token.isBlank()) {
            log.warn("[AR-BFF][EXCHANGE_TOKEN] Rejected: token is null or blank");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseV1.builder()
                    .status("ERROR")
                    .message("Authentication failed")
                    .build()));
        }

        return selfCareValidator.validate(token)
            .doOnSuccess(jwt -> {
                log.info("[AR-BFF][VALIDATE_SC_TOKEN] Token validated: kid={}", jwt.getKeyId());
                log.debug("[AR-BFF][VALIDATE_SC_TOKEN] sub={}", jwt.getSubject());
            })
            .doOnError(e -> log.error("[AR-BFF][VALIDATE_SC_TOKEN] Validation failed: {}", e.getMessage()))
            .flatMap(this::verifyARTokenFields)
            .flatMap(user -> {
                log.info("[AR-BFF][VERIFY_CLAIMS] Claims verified: org_id={}", user.getOrganization().getId());
                log.debug("[AR-BFF][VERIFY_CLAIMS] uid={}", user.getUid());
                return getKeycloakManagerToken()
                    .doOnSuccess(t -> log.info("[AR-BFF][GET_MANAGER_TOKEN] Manager token obtained"))
                    .doOnError(e  -> log.error("[AR-BFF][GET_MANAGER_TOKEN] Failed to obtain manager token: {}", e.getMessage()))
                    .flatMap(managerToken -> upsertKeycloakUser(managerToken, user))
                    .then(Mono.defer(() -> getJwtBearerToken(token)))
                    .doOnSuccess(t -> log.info("[AR-BFF][EXCHANGE_TOKEN] Completed successfully: org_id={}", user.getOrganization().getId()))
                    .map(finalToken -> ResponseEntity.ok(AuthResponseV1.builder()
                                                                        .userInfo(user)
                                                                        .token(finalToken)
                                                                        .build()));
            })
            .onErrorResume(e -> {
                // Log the real cause internally, but never expose internal details to the caller
                log.error("[AR-BFF][EXCHANGE_TOKEN] Failed: {}", e.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponseV1.builder()
                        .status("ERROR")
                        .message("Authentication failed")
                        .build()));
            });
    }

    /**
     * Field validation for the AR token. Checks the presence of required claims and their format.
     *
     * @param jwt the decoded JWT
     * @return {@code Mono<UserDTOV1>} containing the user info extracted
     * from the token if validation is successful, or an error if validation fails
     */
    public Mono<UserDTOV1> verifyARTokenFields(DecodedJWT jwt) {
        return Mono.fromCallable(() -> {
            log.info("[AR-BFF][VERIFY_CLAIMS] Start");
            log.debug("[AR-BFF][VERIFY_CLAIMS] sub={}", jwt.getSubject());
            UserDTOV1 user = new UserDTOV1();

            Map<String, Object> organizationMap = jwt.getClaim("organization").asMap();

            if (organizationMap == null) {
                log.warn("[AR-BFF][VERIFY_CLAIMS] Missing organization claim");
                log.debug("[AR-BFF][VERIFY_CLAIMS] sub={}", jwt.getSubject());
                throw new RuntimeException("Invalid token: organization claim is missing");
            }

            OrganizationDTOV1 org = objectMapper.convertValue(organizationMap, OrganizationDTOV1.class);

            user.setName(jwt.getClaim("name").asString());
            user.setFamilyName(jwt.getClaim("family_name").asString());
            user.setEmail(jwt.getClaim("email").asString());
            user.setUid(jwt.getClaim("uid").asString());
            user.setOrganization(org);

            log.info("[AR-BFF][VERIFY_CLAIMS] Claims valid: org_id={}", org.getId());
            log.debug("[AR-BFF][VERIFY_CLAIMS] uid={} email={}", user.getUid(), user.getEmail());
            return user;
        }).doOnError(e -> log.error("[AR-BFF][VERIFY_CLAIMS] Failed: {}", e.getMessage()));
    }

    /**
     * Get a Keycloak manager token using client credentials grant.
     * The token is cached in memory and proactively refreshed 60s before expiry,
     * so no request ever hits the cold fetch path after the first one.
     *
     * @return {@code Mono<String>} containing the Keycloak token or an error if the request fails
     */
    public Mono<String> getKeycloakManagerToken() {
        CachedToken cached = managerTokenCache.get();
        if (cached != null && cached.isValid()) {
            log.info("[AR-BFF][GET_MANAGER_TOKEN] Using cached manager token");
            return Mono.just(cached.value());
        }
        return fetchAndCacheManagerToken();
    }

    /**
     * Fetches a new manager token from Keycloak, caches it, and schedules a proactive refresh
     * 60 seconds before expiry so subsequent requests always find a warm token.
     */
    private Mono<String> fetchAndCacheManagerToken() {
        log.info("[AR-BFF][GET_MANAGER_TOKEN] Requesting new client_credentials token");

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", managerClientId);
        formData.add("client_secret", managerClientSecret);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> handleKeycloakError("Auth Manager Error", body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(responseMap -> {
                    String token = (String) responseMap.get("access_token");
                    Number expiresIn = (Number) responseMap.getOrDefault("expires_in", 300);
                    long ttl = expiresIn.longValue();
                    managerTokenCache.set(new CachedToken(token, Instant.now().plusSeconds(ttl)));
                    // Schedule proactive refresh 60s before expiry
                    long refreshDelay = Math.max(0, ttl - 60);
                    scheduleProactiveTokenRefresh(refreshDelay);
                    log.info("[AR-BFF][GET_MANAGER_TOKEN] Token cached for {}s, proactive refresh in {}s", ttl, refreshDelay);
                    return token;
                });
    }

    /**
     * Schedules a background token refresh after {@code delaySeconds}.
     * Cancels any previously scheduled refresh to prevent duplicate chains
     * (e.g. from concurrent cold-start fetches).
     * On success, the new token replaces the cache and schedules the next refresh.
     * On failure, logs a warning — the next real request will trigger a sync fetch as fallback.
     */
    private void scheduleProactiveTokenRefresh(long delaySeconds) {
        Disposable newRefresh = Mono.delay(Duration.ofSeconds(delaySeconds))
            .flatMap(ignored -> fetchAndCacheManagerToken())
            .subscribe(
                token -> log.info("[AR-BFF][GET_MANAGER_TOKEN] Token proactively refreshed"),
                e -> log.warn("[AR-BFF][GET_MANAGER_TOKEN] Proactive refresh failed, will retry on next request: {}", e.getMessage())
            );
        // Atomically replace and dispose the previous scheduled refresh to prevent duplicate chains
        Disposable existing = scheduledRefresh.getAndSet(newRefresh);
        if (existing != null && !existing.isDisposed()) {
            existing.dispose();
        }
    }

    /**
     * Create or update a Keycloak user based on the information from the AR token. If the user already exists, it will be updated,
     * otherwise, it will be created. After that, it ensures that the federated identity link is established.
     *
     * @param managerToken the Keycloak token with permissions to manage users
     * @param userDTO the user info extracted from the AR token to be used for user creation/update and linking
     * @return {@code Mono<Void>} indicating the completion of the operation or an error if any step fails
     */
    private Mono<Void> upsertKeycloakUser(String managerToken, UserDTOV1 userDTO) {
        log.info("[AR-BFF][UPSERT_USER] Start");
        log.debug("[AR-BFF][UPSERT_USER] uid={}", userDTO.getUid());

        return getKeycloakUser(userDTO.getUid(), managerToken)
            .flatMap(users -> {
                Map<String, Object> userPayload = buildKeycloakUserPayload(userDTO);

                if (users.isEmpty()) {
                    log.info("[AR-BFF][UPSERT_USER] User not found, creating");
                    return createKeycloakUser(managerToken, userPayload)
                        .doOnSuccess(id -> {
                            log.info("[AR-BFF][UPSERT_USER] User created");
                            log.debug("[AR-BFF][UPSERT_USER] kc_id={}", id);
                        })
                        .flatMap(newUserId -> linkFederatedIdentityToUser(managerToken, newUserId, userDTO.getUid()));
                } else {
                    String internalUserId = (String) users.get(0).get("id");
                    log.info("[AR-BFF][UPSERT_USER] User found, updating");
                    log.debug("[AR-BFF][UPSERT_USER] kc_id={}", internalUserId);
                    return updateKeycloakUser(managerToken, internalUserId, userPayload)
                        .then(linkFederatedIdentityToUser(managerToken, internalUserId, userDTO.getUid()));
                }
            })
            .doOnSuccess(v -> log.info("[AR-BFF][UPSERT_USER] Completed successfully"))
            .doOnError(e  -> log.error("[AR-BFF][UPSERT_USER] Failed: {}", e.getMessage()));
    }

    /**
     * Get list of Keycloak users matching the given username. In our case, we expect either an empty list (user not found)
     * or a list with one user (user found), since we search with "exact=true".
     *
     * @param username the username to search for
     * @param adminToken the Keycloak token with permissions to manage users
     * @return {@code Mono<List<Map<String, Object>>>} containing the list of users matching
     *  the search criteria or an error if the request fails
     */
    public Mono<List<Map<String, Object>>> getKeycloakUser(String username, String adminToken) {
        log.info("[AR-BFF][GET_KC_USER] Searching for user");
        log.debug("[AR-BFF][GET_KC_USER] username={}", username);

        String usersUrl = String.format("%s/admin/realms/%s/users", authServerUrl, realm);
        // Build the URI with query parameters for exact username search
        URI uri = UriComponentsBuilder.fromUriString(usersUrl)
            .queryParam("username", username)
            .queryParam("exact", true)
            .build()
            .toUri();

        return webClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    }

    /**
     * Execute the POST request to create a new user in Keycloak.
     * Returns the internal Keycloak user ID extracted from the {@code Location} response header,
     * avoiding an extra GET round-trip.
     *
     * @param adminToken the Keycloak token with permissions to manage users
     * @param payload the JSON payload for user creation
     * @return {@code Mono<String>} containing the new user's internal Keycloak ID
     */
    private Mono<String> createKeycloakUser(String adminToken, Map<String, Object> payload) {
        log.info("[AR-BFF][CREATE_USER] Sending POST to Keycloak");
        String url = String.format("%s/admin/realms/%s/users", authServerUrl, realm);
        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class).flatMap(body -> handleKeycloakError("Create User Error", body)))
                .toBodilessEntity()
                .map(response -> {
                    String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                    if (location == null) {
                        throw new RuntimeException("createKeycloakUser: Location header missing in Keycloak response");
                    }
                    String newId = location.substring(location.lastIndexOf('/') + 1);
                    log.debug("[AR-BFF][CREATE_USER] kc_id={}", newId);
                    return newId;
                });
    }

    /**
     * Execute the PUT request to update an existing user in Keycloak.
     *
     * @param adminToken the Keycloak token with permissions to manage users
     * @param internalId the internal Keycloak user ID to identify which user to update
     * @param payload the JSON payload for user update
     * @return {@code Mono<Void>} indicating the completion of the operation or an error if the request fails
     */
    private Mono<Void> updateKeycloakUser(String adminToken, String internalId, Map<String, Object> payload) {
        String url = String.format("%s/admin/realms/%s/users/%s", authServerUrl, realm, internalId);
        return webClient.put()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class)
                            .flatMap(body -> handleKeycloakError("Update User Error", body)))
                .toBodilessEntity()
                .then();
    }

    /**
     * Build the JSON payload for Keycloak user creation/update. It contains the basic user info,
     *  the federated identity link to associate the user with the AR token and the organization attributes.
     *
     * @param userDTO the user info extracted from the AR token
     * @return {@code Map<String, Object>} containing the payload for Keycloak user creation or update
     */
    private Map<String, Object> buildKeycloakUserPayload(UserDTOV1 userDTO) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", userDTO.getUid());
        body.put("email", userDTO.getEmail());
        body.put("firstName", userDTO.getName());
        body.put("lastName", userDTO.getFamilyName());
        body.put("enabled", true);
        body.put("emailVerified", true);

        // Link the Idp to the user. This is handled explicitly via the federated-identity API
        // (linkFederatedIdentityToUser) for both create and update, so it is not included here.

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("org_id", List.of(userDTO.getOrganization().getId()));

        if (userDTO.getOrganization().getRoles() != null && !userDTO.getOrganization().getRoles().isEmpty()) {
            // Get first role from the list of roles (in this implementation we expect only one role per user) and add it to the attributes
            String role = userDTO.getOrganization().getRoles().get(0).getRole();
            attributes.put("org_role", List.of(role));
        }

        body.put("attributes", attributes);
        return body;
    }

    /**
     * Link the Keycloak user to the federated identity (the AR token) using the Keycloak API
     *
     * @param adminToken the Keycloak token with permissions to manage users
     * @param internalUserId the internal Keycloak user ID to identify which user to link
     * @param externalUserId the user ID from the AR token to link as federated identity
     * @return {@code Mono<Void>} indicating the completion of the operation or an error if the request fails
     */
    private Mono<Void> linkFederatedIdentityToUser(String adminToken, String internalUserId, String externalUserId) {
        log.info("[AR-BFF][LINK_IDENTITY] Linking to IdP: idp={}", idpAlias);
        log.debug("[AR-BFF][LINK_IDENTITY] uid={} kc_id={}", externalUserId, internalUserId);

        String url = String.format("%s/admin/realms/%s/users/%s/federated-identity/%s",
                authServerUrl, realm, internalUserId, idpAlias);

        Map<String, String> payload = new HashMap<>();
        payload.put("userId", externalUserId);
        payload.put("userName", externalUserId);

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(r -> log.info("[AR-BFF][LINK_IDENTITY] Identity linked successfully"))
                .onErrorResume(WebClientResponseException.class, e -> {
                    String errorBody = e.getResponseBodyAsString();
                    if (errorBody.contains("User is already linked with provider")) {
                        log.info("[AR-BFF][LINK_IDENTITY] Already linked, skipping");
                        return Mono.empty();
                    }
                    log.error("[AR-BFF][LINK_IDENTITY] Failed: {}", errorBody);
                    return Mono.error(new RuntimeException("Federated Identity Link failed: " + errorBody));
                })
                .then();
    }

    /**
     * Execute the token exchange with Keycloak, exchanging the AR token for a Keycloak token with the same user info and roles,
     * using urn:ietf:params:oauth:grant-type:jwt-bearer grant type.
     *
     * @param externalToken the AR token to exchange
     * @return {@code Mono<String>} containing the Keycloak token obtained from the exchange
     *  or an error if the request fails
     */
    public Mono<String> getJwtBearerToken(String externalToken) {
        log.info("[AR-BFF][JWT_BEARER_GRANT] Requesting token exchange");

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", backofficeClientId);
        formData.add("client_secret", backofficeClientSecret);
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        formData.add("assertion", externalToken);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(formData)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("Exchange failed", body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(responseMap -> (String) responseMap.get("access_token"))
                .doOnSuccess(t -> log.info("[AR-BFF][JWT_BEARER_GRANT] Token obtained successfully"))
                .doOnError(e  -> log.error("[AR-BFF][JWT_BEARER_GRANT] Failed: {}", e.getMessage()));
    }

    /**
     * Handle Keycloak error responses by trying to parse the error body as JSON and extract a meaningful message.
     * If the body is not JSON, return the raw body as message.
     *
     * @param context a string to provide context about where the error occurred (e.g. which operation)
     * @param errorBody the raw error body returned by Keycloak
     * @return {@code Mono<Throwable>} containing a RuntimeException with the extracted error message
     */
    private Mono<Throwable> handleKeycloakError(String context, String errorBody) {
        try {
            // Json parsing
            Map<String, Object> errorMap = objectMapper.readValue(errorBody, new TypeReference<Map<String, Object>>() {});
            String description = (String) errorMap.getOrDefault("error_description", errorMap.get("error"));
            return Mono.error(new RuntimeException(description));
        } catch (Exception e) {
            // If the body is not JSON or parsing fails, return the raw body as message
            return Mono.error(new RuntimeException(context + ": " + errorBody));
        }
    }

    /**
     * Creates a new OIDC client in Keycloak and associates it with a predefined group.
     * <p>
     * This method performs an orchestrated reactive flow:
     * <ol>
     *     <li>Obtains a manager access token for authentication.</li>
     *     <li>Sends a POST request to create the client.</li>
     *     <li>Extracts the internal UUID from the response headers.</li>
     *     <li>Retrieves the Service Account User identity automatically created by Keycloak.</li>
     *     <li>Links the Service Account identity to the configured group ID.</li>
     * </ol>
     * </p>
     *
     * @param clientId the unique identifier for the new client (e.g., "my-new-service")
     * @return a {@code Mono<String>} containing the clientId upon successful creation and group association
     */
    public Mono<String> createKeycloakClient(String clientId) {
        log.info("[AR-BFF][CREATE_CLIENT] Starting process for clientId={}", clientId);
        
        return getKeycloakManagerToken()
            .flatMap(adminToken -> {
                URI createUri = buildKeycloakUri("/admin/realms/{realm}/clients", null);
                
                return webClient.post()
                    .uri(createUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildClientPayload(clientId))
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.CONFLICT), response ->
                        // Client already exists
                        Mono.error(new ResponseStatusException(
                            HttpStatus.CONFLICT, KEYCLOAK_CLIENT_ALREADY_EXISTS)))
                    .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                            .flatMap(body -> handleKeycloakError("Create Client Error", body)))
                    .toBodilessEntity()
                    .flatMap(response -> {
                        // Get the internal client ID from the Location header to perform subsequent operations
                        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                        
                        if (location == null) {
                            return Mono.error(new RuntimeException("Create Client Error: Location header missing"));
                        }
                        String internalClientId = location.substring(location.lastIndexOf('/') + 1);
                        log.debug("[AR-BFF][CREATE_CLIENT] Client created. Internal ID: {}. Proceeding with group association.", internalClientId);
                        
                        //Execute operation in parallel: get group ID and service account user ID, then add user to group
                        return Mono.zip(
                            getGroupIdByName(adminToken, tppGroupName),
                            getServiceAccountUserId(adminToken, internalClientId))
                        .flatMap(tuple -> addUserToGroup(adminToken, tuple.getT2(), tuple.getT1()))
                        .thenReturn(clientId);
                    });
            })
            .doOnSuccess(res -> log.info("[AR-BFF][CREATE_CLIENT] Successfully created client: {}", clientId))
            .doOnError(e -> log.error("[AR-BFF][CREATE_CLIENT] Failed: {}", e.getMessage()));
    }

    /**
     * Builds the JSON payload for a standard OIDC Confidential client creation.
     * <p>
     * The client is configured with Service Accounts enabled to allow it to have a
     * user identity, which is required for group membership and role assignments.
     * </p>
     *
     * @param clientId the identifier to be assigned as both clientId and name
     * @return a {@code Map<String, Object>} representing the Keycloak ClientRepresentation payload
     */
    private Map<String, Object> buildClientPayload(String clientId) {
        log.info("[AR-BFF][BUILD_CLIENT_PAYLOAD] Building payload for clientId={}", clientId);
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
        
        // Client Secret (Confidential)
        Map<String, String> attributes = new HashMap<>();
        attributes.put("use.refresh.tokens", "true");
        client.put("attributes", attributes);
        
        return client;
    }

    /**
     * Resolves a Keycloak Group Name to its corresponding internal unique identifier (UUID).
     * <p>
     * Since the Keycloak Admin API "search" parameter performs a fuzzy match, this method 
     * retrieves a list of potential candidates and applies a strict filter to ensure 
     * an exact match with the provided {@code groupName}.
     * </p>
     *
     * @param adminToken the administrative access token required for Keycloak Admin API calls
     * @param groupName  the exact name of the group to be resolved
     * @return a {@code Mono<String>} emitting the internal UUID of the group if found;
     *         otherwise, emits a {@link RuntimeException} if the group does not exist
     *         or the search returns no exact matches.
     */
    private Mono<String> getGroupIdByName(String adminToken, String groupName) {
        log.debug("[AR-BFF][GET_GROUP_ID] Searching for group: {}", groupName);
        String groupsUrl = String.format("%s/admin/realms/%s/groups", authServerUrl, realm);

        URI uri = UriComponentsBuilder.fromUriString(groupsUrl)
        .queryParam("search", groupName)
        .build()
        .toUri();

        return webClient.get()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        .flatMap(groups -> groups.stream()
            .filter(g -> groupName.equals(g.get("name")))
            .map(g -> (String) g.get("id"))
            .findFirst()
            .map(Mono::just)
            .orElseGet(() -> Mono.error(new RuntimeException("Group not found: " + groupName))));
    }

    /**
     * Retrieves the internal unique identifier (UUID) of the Service Account User
     * associated with a specific Keycloak client.
     *
     * @param adminToken the manager token with administrative privileges
     * @param internalClientId the internal UUID of the client (not the human-readable clientId)
     * @return a {@code Mono<String>} containing the internal User ID of the service account
     */
    private Mono<String> getServiceAccountUserId(String adminToken, String internalClientId) {
        
        URI uri = buildKeycloakUri("/admin/realms/{realm}/clients/" + internalClientId + "/service-account-user", null);
        
        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class).flatMap(body -> handleKeycloakError("Get Service Account Error", body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(user -> (String) user.get("id"));
    }

    /**
     * Associates a specific user (including service accounts) with a Keycloak group.
     * <p>
     * This method executes a PUT request to the Keycloak Admin API to link an identity
     * to a specific group path.
     * </p>
     *
     * @param adminToken the manager token with administrative privileges
     * @param userId the internal UUID of the user to be added
     * @param groupId the internal UUID of the group
     * @return a {@code Mono<Void>} that completes when the operation is finished
     */
    private Mono<Void> addUserToGroup(String adminToken, String userId, String groupId) {
        URI uri = buildKeycloakUri("/admin/realms/{realm}/users/" + userId + "/groups/" + groupId, null);

        return webClient.put()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("Add User to Group Error", body)))
                .toBodilessEntity()
                .then();
    }

    /**
     * Utility method to construct a fully qualified Keycloak URI.
     * <p>
     * This helper ensures that the {@code authServerUrl} is always used as the absolute base,
     * preventing issues where the {@code WebClient} might incorrectly append paths to a 
     * pre-configured base URL. It also handles path variable expansion and query parameters.
     * </p>
     *
     * @param path The relative API path, which can include placeholders like {realm}.
     * @param queryParams An optional map of query parameters to be appended to the URI.
     * @return A complete and encoded {@link URI} instance.
     */
    private URI buildKeycloakUri(String path, Map<String, String> queryParams) {
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerUrl)
                .path(path);
                
        if (queryParams != null) {
            //Add param to url
            queryParams.forEach(builder::queryParam);
        }
        
        // The buildAndExpand method is used to replace URI template variables.
        // In this case, it resolves the "{realm}" placeholder commonly found in Keycloak
        // administrative paths with the actual realm value configured for this service.
        return builder.buildAndExpand(realm).toUri();
    }

}
