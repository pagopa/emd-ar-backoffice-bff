package it.gov.pagopa.emd.ar.backoffice.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;

import it.gov.pagopa.emd.ar.backoffice.dto.ResponseDTO;
import it.gov.pagopa.emd.ar.backoffice.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class BackofficeServiceImpl implements BackofficeService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private WebClient webClient;

    private AuthService authService;

    public BackofficeServiceImpl(WebClient webClient, AuthService authService) {
        this.webClient = webClient;
        this.authService = authService;
    }

    @Override
    public Mono<ResponseEntity<ResponseDTO>> getToken(Jwt jwt) {
        log.info("BackofficeServiceImpl - getToken()");

        String externalToken = jwt.getTokenValue(); // Recupera la stringa JWT originale

        //Validazione e recupero info dal token di selfcare
        UserDTO user = authService.verifyTokenFields(jwt);

        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseDTO("ERROR", "Token non valido o incompleto", null)));
        }

        // Prendi token Admin
        return getKeycloakAccessToken()
            .flatMap(adminToken -> 
            // Sincronizziamo l'utente (Upsert)
            upsertKeycloakUser(adminToken, user)
            // Token Exchange
            .then(Mono.defer(() -> getJwtBearerToken(externalToken))))
            // Token da restituire al FE
            .map(finalToken -> ResponseEntity.ok(new ResponseDTO("Success", "Token exchanged", finalToken)
        ))
        .onErrorResume(e -> {
            log.error("Errore nel processo di sync: {}", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ResponseDTO("ERROR", "Errore durante l'upsert su Keycloak", null)));
        });

        
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
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        System.out.println("Requesting token from Keycloak at: " + tokenUrl);

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
    private Mono<String> upsertKeycloakUser(String adminToken, UserDTO userDTO) {
        log.info("upsertKeycloakUser() - Start for user: {}", userDTO.getUid());

        return getUsersFromKeycloak(userDTO.getUid(), adminToken)
            .flatMap(users -> {
                Map<String, Object> userPayload = buildKeycloakUserPayload(userDTO);

                if (users.isEmpty()) {
                    // UTENTE NON ESISTE -> CREAZIONE (POST)
                    log.info("upsertKeycloakUser() - User not found, creating new user");
                    return createKeycloakUser(adminToken, userPayload);
                } else {
                    // UTENTE ESISTE -> AGGIORNAMENTO (PUT)
                    String internalId = (String) users.get(0).get("id");
                    log.info("upsertKeycloakUser() - User found with ID: {}, updating...", internalId);
                    return updateKeycloakUser(adminToken, internalId, userPayload);
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
    private Mono<String> createKeycloakUser(String adminToken, Map<String, Object> payload) {
        log.info("createKeycloakUser() - Creating new user");
        String url = String.format("%s/admin/realms/%s/users", authServerUrl, realm);
        return webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .thenReturn("User Created");
    }

    /**
     * Esegue la PUT di aggiornamento.
     */
    private Mono<String> updateKeycloakUser(String adminToken, String internalId, Map<String, Object> payload) {
        String url = String.format("%s/admin/realms/%s/users/%s", authServerUrl, realm, internalId);
        return webClient.put()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .thenReturn("User Updated");
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
        federatedIdentity.put("identityProvider", "jwt-authorization-grant"); // L'alias del tuo IdP
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
     * Effettua lo scambio del token esterno (assertion) con un token Keycloak 
     * utilizzando il grant type urn:ietf:params:oauth:grant-type:jwt-bearer.
     */
    public Mono<String> getJwtBearerToken(String externalToken) {
        log.info("getJwtBearerToken() - Requesting token exchange via JWT Bearer Grant");
        
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        // Prepariamo i parametri della form
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
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
