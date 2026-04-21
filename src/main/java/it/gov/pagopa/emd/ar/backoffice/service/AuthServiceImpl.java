package it.gov.pagopa.emd.ar.backoffice.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.http.MediaType;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.OrganizationDTOV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.UserDTOV1;
import lombok.extern.slf4j.Slf4j;
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

    private final ReactiveJwtDecoder jwtDecoder;
    private WebClient webClient;
    private final ObjectMapper objectMapper;

    public AuthServiceImpl(WebClient webClient, ReactiveJwtDecoder jwtDecoder, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ResponseEntity<AuthResponseV1>> exchangeToken(String token) {
        log.info("AuthService - exchangeToken()");

        // Manual decode and validation of the AR token
        return jwtDecoder.decode(token)
            .flatMap(this::verifyARTokenFields)
            .flatMap(user -> getKeycloakManagerToken()
                    .flatMap(managerToken ->
                        // User Upsert (create/update + link federated identity)
                        upsertKeycloakUser(managerToken, user))
                        // Token Exchange
                        .then(Mono.defer(() -> getJwtBearerToken(token)))
                        // Final response
                        .map(finalToken -> ResponseEntity.ok(AuthResponseV1.builder()
                                                                            .userInfo(user)
                                                                            .token(finalToken)
                                                                            .build()))
                    )
                    .onErrorResume(e -> {
                        log.error("Authentication process failed: {}", e.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(AuthResponseV1.builder()
                                                    .status("ERROR")
                                                    .message(e.getMessage())
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
    public Mono<UserDTOV1> verifyARTokenFields(Jwt jwt) {
        return Mono.fromCallable(() -> {
            log.info("verifyARTokenFields() for sub: {}", jwt.getSubject());
            UserDTOV1 user = new UserDTOV1();
            
            //Get the "organization" claim from the AR token
            Map<String, Object> organizationMap = jwt.getClaim("organization");

            if (organizationMap == null) {
                log.warn("verifyARTokenFields() - Validation failed: organization claim missing for sub: {}", jwt.getSubject());
                throw new RuntimeException("Invalid token: organization claim is missing");
            }

            OrganizationDTOV1 org = objectMapper.convertValue(organizationMap, OrganizationDTOV1.class);

            user.setName(jwt.getClaimAsString("name"));
            user.setFamilyName(jwt.getClaimAsString("family_name"));
            user.setEmail(jwt.getClaimAsString("email"));
            user.setUid(jwt.getClaimAsString("uid"));
            user.setOrganization(org);

            log.info("verifyARTokenFields() - Validation successful for subject: {}: {}", jwt.getSubject());
            return user;
        }).doOnError(e -> log.error("verifyARTokenFields() - Token validation error: {}", e.getMessage()));
    }

    // Consider using cache
    /**
     * Get a Keycloak token using client credentials grant. 
     * This token is used for for user management operations (create/update).
     * 
     * @return {@code Mono<String>} containing the Keycloak token or an error if the request fails
     */
    public Mono<String> getKeycloakManagerToken() {
        log.info("getKeycloakManagerToken()");

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        // WebClient manage the form data encoding for us, we just need to pass a MultiValueMap
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", managerClientId);
        formData.add("client_secret", managerClientSecret);

        log.info("Requesting token from Keycloak at: " + tokenUrl);

        Mono<String> token = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> 
                    response.bodyToMono(String.class)
                        .flatMap(body -> handleKeycloakError("Auth Manager Error", body)))
                .bodyToMono(Map.class) // Transform the response into a Map to extract the token
                .map(responseMap -> (String) responseMap.get("access_token"));
        return token;
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
        log.info("upsertKeycloakUser() - Start for user: {}", userDTO.getUid());

        return getKeycloakUser(userDTO.getUid(), managerToken)
            .flatMap(users -> {
                Map<String, Object> userPayload = buildKeycloakUserPayload(userDTO);

                if (users.isEmpty()) {
                    // USER DOES NOT EXIST -> CREATION (POST)
                    log.info("upsertKeycloakUser() - User not found, creating new user");
                    return createKeycloakUser(managerToken, userPayload)
                    .then();
                } else {
                    // USER EXISTS -> UPDATE (PUT)
                    String internalUserId = (String) users.get(0).get("id");
                    log.info("upsertKeycloakUser() - User found with ID: {}, updating...", internalUserId);
                    return updateKeycloakUser(managerToken, internalUserId, userPayload)
                        .then(linkFederatedIdentityToUser(managerToken, internalUserId, userDTO.getUid()));
                }
            });
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
        log.info("getKeycloakUser() - searching for: {}", username);
        
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
     * 
     * @param adminToken the Keycloak token with permissions to manage users
     * @param payload the JSON payload for user creation
     * @return {@code Mono<Void>} indicating the completion of the operation or an error if the request fails
     */
    private Mono<Void> createKeycloakUser(String adminToken, Map<String, Object> payload) {
        log.info("createKeycloakUser() - Creating new user");
        String url = String.format("%s/admin/realms/%s/users", authServerUrl, realm);
        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .then();
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

        // Link the federated identity (the AR token) to the user. This is needed both in creation and update, 
        // because in case of update we could have an existing user without a link to the IdP, and we need 
        // to ensure the link is always present after this operation.
        Map<String, String> federatedIdentity = new HashMap<>();
        federatedIdentity.put("identityProvider", idpAlias); // Idp sefcare alias on keycloak
        federatedIdentity.put("userId", userDTO.getUid());       // User id from token
        federatedIdentity.put("userName", userDTO.getUid());
        
        body.put("federatedIdentities", List.of(federatedIdentity));

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
        
        log.info("linkFederatedIdentityToUser() - Linking user {} to IdP {} for internalId {}", 
                externalUserId, idpAlias, internalUserId);

        String url = String.format("%s/admin/realms/%s/users/%s/federated-identity/%s", 
                                authServerUrl, realm, internalUserId, idpAlias);

        // Build Json payload for the link request
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
                .onErrorResume(WebClientResponseException.class, e -> {
                    // Read the error body from Keycloak
                    String errorBody = e.getResponseBodyAsString();
                    if (errorBody.contains("User is already linked with provider")) {
                    log.info("The user {} is already linked to IdP {}, proceeding.", externalUserId, idpAlias);
                    return Mono.empty(); // Error ignored, link already present
                }
                log.error("Error during federated identity linking: {}", errorBody);
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
        log.info("getJwtBearerToken() - Requesting token exchange via JWT Bearer Grant");
        
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        // Build form data for the token exchange request
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", backofficeClientId);
        formData.add("client_secret", backofficeClientSecret);
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        formData.add("assertion", externalToken); // Selfcare token

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(formData)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(body -> handleKeycloakError("Exchange failed", body));
                        })
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(responseMap -> (String) responseMap.get("access_token"))
                .doOnError(e -> log.error("Errore fatale nello scambio token: {}", e.getMessage()));

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
            Map<String, Object> errorMap = objectMapper.readValue(errorBody, Map.class);
            String description = (String) errorMap.getOrDefault("error_description", errorMap.get("error"));
            return Mono.error(new RuntimeException(description));
        } catch (Exception e) {
            // If the body is not JSON or parsing fails, return the raw body as message
            return Mono.error(new RuntimeException(context + ": " + errorBody));
        }
    }

}
