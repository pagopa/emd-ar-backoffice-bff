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

import it.gov.pagopa.emd.ar.backoffice.dto.OrganizationDTO;
import it.gov.pagopa.emd.ar.backoffice.dto.ResponseDTO;
import it.gov.pagopa.emd.ar.backoffice.dto.UserDTO;
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

    @Value("${keycloak.token-exchange.client-id}")
    private String tokenExchangeClientId;

    @Value("${keycloak.token-exchange.client-secret}")
    private String tokenExchangeClientSecret;

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

    @Override
    public Mono<ResponseEntity<ResponseDTO>> getToken(String token) {
        log.info("BackofficeServiceImpl - getToken()");

        // Decodifica manuale del token stringa in oggetto Jwt
        return jwtDecoder.decode(token)
            .flatMap(this::verifyTokenFields)
            .flatMap(user -> getKeycloakAccessToken()
                    .flatMap(managerToken ->
                        // Sincronizziamo l'utente (Upsert)
                        upsertKeycloakUser(managerToken, user))
                        // Token Exchange
                        .then(Mono.defer(() -> getJwtBearerToken(token)))
                    )
                    // Token da restituire al FE
                    .map(finalToken -> ResponseEntity.ok(new ResponseDTO("Success", "Token exchanged", finalToken)))
                    .onErrorResume(e -> {
                        log.error("Errore validazione token o processo: {}", e.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new ResponseDTO("ERROR", "Token non valido: " + e.getMessage(), null)));
            });
    }

    public Mono<UserDTO> verifyTokenFields(Jwt jwt) {
        return Mono.fromCallable(() -> {
            log.info("AuthService - verifyTokenFields() for sub: {}", jwt.getSubject());
            UserDTO user = new UserDTO();
            
            //Recupera la mappa "organization" dal token
            Map<String, Object> organizationMap = jwt.getClaim("organization");

            if (organizationMap == null) {
                log.warn("AuthService - Validazione token fallita per sub: {}", jwt.getSubject());
                throw new RuntimeException("Token incompleto: organization claim mancante");
            }

            OrganizationDTO org = objectMapper.convertValue(organizationMap, OrganizationDTO.class);

            user.setName(jwt.getClaimAsString("name"));
            user.setFamilyName(jwt.getClaimAsString("family_name"));
            user.setEmail(jwt.getClaimAsString("email"));
            user.setUid(jwt.getClaimAsString("uid"));
            user.setOrganization(org);

            log.info("AuthService - Validazione token riuscita per sub: {}", jwt.getSubject());
            return user;
        }).doOnError(e -> log.error("Errore validazione token: {}", e.getMessage()));
    }

    // Aggiungere token nella cache? La persistenza del token viene gestita lato FE? Serve o no la cache?
    /**
     * Metodo per ottenere un token di accesso da Keycloak usando le credenziali del client.
     * NB. Il client DEVE avere il ruolo "manage-users"
     * @return
     */
    public Mono<String> getKeycloakAccessToken() {
        log.info("getKeycloakAccessToken()");

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        // WebClient gestisce automaticamente il Content-Type se passi una MultiValueMap
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
                    response.bodyToMono(String.class).flatMap(error -> 
                        Mono.error(new RuntimeException("Keycloak Auth Error: " + error))))
                .bodyToMono(Map.class) // Trasforma il JSON di risposta in una Map
                .map(responseMap -> (String) responseMap.get("access_token"));
        return token;
    }

    /**
     * Crea o aggiorna un utente su Keycloak basandosi sullo username (UID).
     */
    private Mono<Void> upsertKeycloakUser(String managerToken, UserDTO userDTO) {
        log.info("upsertKeycloakUser() - Start for user: {}", userDTO.getUid());

        return getUsersFromKeycloak(userDTO.getUid(), managerToken)
            .flatMap(users -> {
                Map<String, Object> userPayload = buildKeycloakUserPayload(userDTO);

                if (users.isEmpty()) {
                    // UTENTE NON ESISTE -> CREAZIONE (POST)
                    log.info("upsertKeycloakUser() - User not found, creating new user");
                    return createKeycloakUser(managerToken, userPayload)
                    .then();
                } else {
                    // UTENTE ESISTE -> AGGIORNAMENTO (PUT)
                    String internalUserId = (String) users.get(0).get("id");
                    log.info("upsertKeycloakUser() - User found with ID: {}, updating...", internalUserId);
                    return updateKeycloakUser(managerToken, internalUserId, userPayload)
                        .then(linkFederatedIdentity(managerToken, internalUserId, userDTO.getUid()));
                }
            });
    }

    /**
     * Recupera la lista degli utenti da Keycloak.
     * Supporta la ricerca opzionale per username.
     */
    public Mono<List<Map<String, Object>>> getUsersFromKeycloak(String username, String adminToken) {
        log.info("getUsersFromKeycloak() - searching for: {}", username);
        
        String usersUrl = String.format("%s/admin/realms/%s/users", authServerUrl, realm);
        // Costruiamo l'URI completo includendo i query parameters
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
     * Esegue la POST di creazione.
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
     * Esegue la PUT di aggiornamento.
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
     * Costruisce il JSON per Keycloak.
     */
    private Map<String, Object> buildKeycloakUserPayload(UserDTO userDTO) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", userDTO.getUid());
        body.put("email", userDTO.getEmail());
        body.put("firstName", userDTO.getName());
        body.put("lastName", userDTO.getFamilyName());
        body.put("enabled", true);
        body.put("emailVerified", true);

        // Questo collega l'utente all'Identity Provider creato su Keycloak
        Map<String, String> federatedIdentity = new HashMap<>();
        federatedIdentity.put("identityProvider", idpAlias); // L'alias dell'idp di sefcare presente su keycloak
        federatedIdentity.put("userId", userDTO.getUid());       // L'ID dell'utente presente nel token
        federatedIdentity.put("userName", userDTO.getUid());
        
        body.put("federatedIdentities", List.of(federatedIdentity));

        // Gestione attributi (devono essere liste di stringhe)
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("org_id", List.of(userDTO.getOrganization().getId()));
        
        if (userDTO.getOrganization().getRoles() != null && !userDTO.getOrganization().getRoles().isEmpty()) {
            // Prendi il primo ruolo dell'organizzazione
            String role = userDTO.getOrganization().getRoles().get(0).getRole();
            attributes.put("org_role", List.of(role));
        }
        
        body.put("attributes", attributes);
        return body;
    }

    /**
     * Collega un'identità federata a un utente esistente su Keycloak.
     */
    private Mono<Void> linkFederatedIdentity(String adminToken, String internalUserId, String externalUserId) {
        
        log.info("linkFederatedIdentity() - Linking user {} to IdP {} for internalId {}", 
                externalUserId, idpAlias, internalUserId);

        String url = String.format("%s/admin/realms/%s/users/%s/federated-identity/%s", 
                                authServerUrl, realm, internalUserId, idpAlias);

        // Prepariamo il payload JSON
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
                    // Leggiamo il corpo dell'errore da Keycloak
                    String errorBody = e.getResponseBodyAsString();
                    if (errorBody.contains("User is already linked with provider")) {
                    log.info("L'utente {} è già collegato all'IdP {}, procedo oltre.", externalUserId, idpAlias);
                    return Mono.empty(); // Errore ignorato, link già presente
                }
                log.error("Errore durante il link dell'identità federata: {}", errorBody);
                return Mono.error(new RuntimeException("Federated Identity Link failed: " + errorBody));
            })
            .then(); // Ritorna Mono<Void>
    }

    /**
     * Effettua lo scambio del token esterno (assertion) con un token Keycloak 
     * utilizzando il grant type urn:ietf:params:oauth:grant-type:jwt-bearer.
     */
    public Mono<String> getJwtBearerToken(String externalToken) {
        log.info("getJwtBearerToken() - Requesting token exchange via JWT Bearer Grant");
        
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        // Prepariamo i parametri della form
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", tokenExchangeClientId);
        formData.add("client_secret", tokenExchangeClientSecret);
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        formData.add("assertion", externalToken); // Il token JWT ricevuto da selfcare

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(formData)
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Errore durante il JWT Bearer Grant: {}", errorBody);
                                return Mono.error(new RuntimeException("Exchange failed: " + errorBody));
                            });
                })
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(responseMap -> (String) responseMap.get("access_token"))
                .doOnError(e -> log.error("Errore fatale nello scambio token: {}", e.getMessage()));

    }

}
