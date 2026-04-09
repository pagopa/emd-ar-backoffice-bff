package it.gov.pagopa.emd.ar.backoffice.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import it.gov.pagopa.emd.ar.backoffice.dto.ResponseDTO;
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
    public Mono<ResponseEntity<ResponseDTO>> getToken(String header) {
        
        log.info("getToken()");

        String headerToken = header.substring(7);

        if (authService.verifyTokenFields(headerToken)){
            return getKeycloakAccessToken().map(actualTokenString -> {
                // QUI il token è una String vera, estratta dal Mono!
                return ResponseEntity.ok(
                    new ResponseDTO("Success", "Token received successfully", actualTokenString));
            });
        } else {
            // Se il controllo fallisce, restituiamo un Mono già pronto con l'errore
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseDTO("ERROR", "Token non valido", StringUtils.EMPTY)));
        }
    }

    // Aggiungere token nella cache? La persistenza del token viene gestita lato FE? Serve o no la cache?
    public Mono<String> getKeycloakAccessToken() {

        log.info("getKeycloakAccessToken()");

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        // WebClient gestisce automaticamente il Content-Type se passi una MultiValueMap
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(Map.class) // Trasforma il JSON di risposta in una Map
                .map(responseMap -> (String) responseMap.get("access_token"));
    }

}
