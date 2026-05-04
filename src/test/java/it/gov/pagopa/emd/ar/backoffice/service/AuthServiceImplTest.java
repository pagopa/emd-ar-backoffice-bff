package it.gov.pagopa.emd.ar.backoffice.service;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthResponseV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private WebClient webClient;

    @Mock
    private SelfCareTokenValidator selfCareValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthServiceImpl authService;

    /**
     * Inizializzazione dell'ambiente di test prima di ogni esecuzione.
     * <p>
     * Poiché il test non carica il contesto Spring, i campi annotati con {@code @Value} nella classe
     * {@link AuthServiceImpl} risulterebbero null. Viene utilizzato  {@link ReflectionTestUtils}
     * per iniettare manualmente i valori di configurazione necessari (URL, credenziali Keycloak, alias IdP).
     * Viene inoltre iniettata un'istanza reale di {@link ObjectMapper} per garantire una conversione fedele dei claim JSON.
     * </p>
     */
    @BeforeEach
    void setUp() {
        // Iniezione manuale delle configurazioni di ambiente
        ReflectionTestUtils.setField(authService, "authServerUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(authService, "realm", "test-realm");
        ReflectionTestUtils.setField(authService, "managerClientId", "m-id");
        ReflectionTestUtils.setField(authService, "managerClientSecret", "m-secret");
        ReflectionTestUtils.setField(authService, "backofficeClientId", "b-id");
        ReflectionTestUtils.setField(authService, "backofficeClientSecret", "b-secret");
        ReflectionTestUtils.setField(authService, "idpAlias", "idp-test");
        ReflectionTestUtils.setField(authService, "objectMapper", objectMapper);
    }

    /**
     * Testa il caso di successo del metodo {@code exchangeToken} per un nuovo utente.
     * <p>
     * Lo scenario prevede:
     * <ol>
     *     <li>Validazione corretta del token AR e decodifica dei relativi Claim (UserInfo).</li>
     *     <li>Simulazione della chiamata a Keycloak per la ricerca dell'utente, che restituisce una lista vuota (utente non censito).</li>
     *     <li>Ottenimento del token di management tramite Client Credentials.</li>
     *     <li>Creazione del nuovo utente su Keycloak tramite chiamata POST.</li>
     *     <li>Esecuzione finale dello scambio token tramite JWT Bearer Grant.</li>
     * </ol>
     * </p>
     * <p>
     * Il test utilizza {@link StepVerifier} per gestire la natura asincrona del flusso Reactor,
     * verificando che il risultato sia un {@link ResponseEntity} con stato HTTP 200 (OK)
     * e contenente il token Keycloak finale.
     * </p>
     *
     * @see AuthServiceImpl#exchangeToken(String)
     */
    @Test
    @SuppressWarnings("unchecked")
    void exchangeToken_Success_NewUser() {
        // PREPARAZIONE MOCK DEI CLAIM - Devono essere completati PRIMA del JWT

        // Claim Organizzazione
        Claim orgClaim = mock(Claim.class);
        Map<String, Object> orgData = Map.of(
            "id", "ORG1",
            "name", "Org Name",
            "roles", List.of(Map.of("role", "admin"))
        );
        when(orgClaim.asMap()).thenReturn(orgData);

        // Claim Stringa
        Claim nameClaim = mock(Claim.class);
        when(nameClaim.asString()).thenReturn("Mario");

        Claim familyNameClaim = mock(Claim.class);
        when(familyNameClaim.asString()).thenReturn("Rossi");

        Claim emailClaim = mock(Claim.class);
        when(emailClaim.asString()).thenReturn("mario@rossi.it");

        Claim uidClaim = mock(Claim.class);
        when(uidClaim.asString()).thenReturn("mario_uid");

        // CONFIGURAZIONE JWT (Usa i mock pronti sopra)
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        when(selfCareValidator.validate(anyString())).thenReturn(Mono.just(decodedJWT));

        when(decodedJWT.getClaim("organization")).thenReturn(orgClaim);
        when(decodedJWT.getClaim("name")).thenReturn(nameClaim);
        when(decodedJWT.getClaim("family_name")).thenReturn(familyNameClaim);
        when(decodedJWT.getClaim("email")).thenReturn(emailClaim);
        when(decodedJWT.getClaim("uid")).thenReturn(uidClaim);
        when(decodedJWT.getSubject()).thenReturn("mario_uid");

        // CONFIGURAZIONE WEBCLIENT
        WebClient.RequestBodyUriSpec postUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec postBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec postHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec postResponseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postUriSpec);
        when(postUriSpec.uri(anyString())).thenReturn(postBodySpec);
        when(postBodySpec.contentType(any())).thenReturn(postBodySpec);
        when(postBodySpec.accept(any())).thenReturn(postBodySpec);
        when(postBodySpec.header(any(), any())).thenReturn(postBodySpec);
        when(postBodySpec.bodyValue(any())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.retrieve()).thenReturn(postResponseSpec);
        when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);

        // Risposte sequenziali:
        // Manager Token (chiamata a bodyToMono(Map.class))
        // Prima chiamata (fetchAndCacheManagerToken) -> manager-token
        // Seconda chiamata (getJwtBearerToken) -> final-kc-token
        when(postResponseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Map.of("access_token", "manager-token")))
                .thenReturn(Mono.just(Map.of("access_token", "final-kc-token")));

        
        // GESTIONE toBodilessEntity (Create User e Link Identity)
        // Prepariamo un header Location per la creazione utente
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(org.springframework.http.HttpHeaders.LOCATION, "/users/new-user-uuid");

        // Prima chiamata (createKeycloakUser) -> 201 Created con Location
        // Seconda chiamata (linkFederatedIdentityToUser) -> 200 OK
        when(postResponseSpec.toBodilessEntity())
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.CREATED).headers(headers).build()))
                .thenReturn(Mono.just(ResponseEntity.ok().build()));


        // Mock GET per ricerca utente (getKeycloakUser)
        WebClient.RequestHeadersUriSpec getUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec getHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec getResponseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri(any(java.net.URI.class))).thenReturn(getHeadersSpec);
        when(getHeadersSpec.header(any(), any())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);

        when(getResponseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Collections.emptyList()));

        // ESECUZIONE
        Mono<ResponseEntity<AuthResponseV1>> result = authService.exchangeToken("test-token");

        // VERIFICA
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals("final-kc-token", response.getBody().getToken());
                    assertEquals("Mario", response.getBody().getUserInfo().getName());
                })
                .verifyComplete();
    }
}
