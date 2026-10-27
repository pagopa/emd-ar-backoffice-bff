package it.gov.pagopa.emd.ar.backoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakClientServiceImpl;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests per il metodo {@code getPagopaClientCredentials} di {@link KeycloakClientServiceImpl}.
 *
 * <p>Segue lo stesso pattern di {@link KeycloakClientServiceDeleteTest}: usa un {@link ExchangeFunction}
 * iniettato per intercettare le chiamate HTTP senza spinning up di server reali.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path: GET /clients → 200 con internalId, GET /client-secret → 200 con secret</li>
 *   <li>Client non trovato (lista vuota) → {@link ResourceNotFoundException} (404)</li>
 *   <li>GET /clients fallisce con 500 → {@link ExternalServiceException} propagata</li>
 *   <li>GET /client-secret fallisce con 500 → {@link ExternalServiceException} propagata</li>
 *   <li>GET /client-secret risponde senza campo "value" → {@link ExternalServiceException}</li>
 *   <li>DTO toString() non espone il clientSecret in chiaro</li>
 *   <li>Il campo grantType del DTO è sempre "client_credentials"</li>
 *   <li>DTO toString() include il grantType in chiaro</li>
 * </ol>
 * </p>
 */
class KeycloakClientServiceGetPagopaCredentialsTest {

    private static final String BASE_URL    = "https://keycloak.test";
    private static final String REALM       = "test-realm";
    private static final String TPP_GROUP   = "tpp-group";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String CLIENT_ID   = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";
    private static final String INTERNAL_ID = "kc-internal-uuid-001";
    private static final String SECRET      = "xYz123AbC456DeF789GhI012JkL345Mn";
    private static final String GRANT_TYPE  = "client_credentials";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse ok(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private ClientResponse serverError() {
        return ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"server_error\",\"error_description\":\"Internal error\"}")
                .build();
    }

    private KeycloakClientServiceImpl serviceWith(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        KeycloakTokenService tokenService = mock(KeycloakTokenService.class);
        when(tokenService.getManagerToken()).thenReturn(Mono.just(ADMIN_TOKEN));
        return new KeycloakClientServiceImpl(webClient, new ObjectMapper(), tokenService,
                BASE_URL, REALM, TPP_GROUP);
    }

    private ExchangeFunction queuedExchange(Queue<ClientResponse> responses,
                                             List<String> recordedRequests) {
        return request -> {
            recordedRequests.add(request.method().name() + " " + request.url().getPath());
            ClientResponse next = responses.poll();
            if (next == null) {
                throw new IllegalStateException("Risposta non attesa: "
                        + request.method() + " " + request.url());
            }
            return Mono.just(next);
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy path — flusso in 2 chiamate:
     *   1. GET /clients?clientId={tppId}&exact=true → 200 [{id: INTERNAL_ID}]
     *   2. GET /clients/{INTERNAL_ID}/client-secret → 200 {"type":"secret","value":"..."}
     * Atteso: {@link TppPagopaCredentialsDTOV1} con clientId e clientSecret corretti.
     */
    @Test
    void getPagopaClientCredentials_Success_ReturnsBothCallsInOrder() {
        String clientsJson = "[{\"id\":\"" + INTERNAL_ID + "\",\"clientId\":\"" + CLIENT_ID + "\"}]";
        String secretJson  = "{\"type\":\"secret\",\"value\":\"" + SECRET + "\"}";

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientsJson),  // GET /clients?clientId=...&exact=true
                ok(secretJson)    // GET /clients/{INTERNAL_ID}/client-secret
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).getPagopaClientCredentials(CLIENT_ID))
                .assertNext(credentials -> {
                    assertThat(credentials.getClientId()).isEqualTo(CLIENT_ID);
                    assertThat(credentials.getClientSecret()).isEqualTo(SECRET);
                    assertThat(credentials.getGrantType()).isEqualTo(GRANT_TYPE);
                })
                .verifyComplete();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
        assertThat(calls.get(1)).startsWith("GET").contains("/clients/" + INTERNAL_ID + "/client-secret");
    }

    /**
     * Client non trovato: GET /clients restituisce lista vuota.
     * Atteso: {@link ResourceNotFoundException} con il tppId nel messaggio.
     * La seconda chiamata (client-secret) NON deve essere eseguita.
     */
    @Test
    void getPagopaClientCredentials_ClientNotFound_ThrowsResourceNotFoundException() {
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok("[]")  // GET /clients → nessun client
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).getPagopaClientCredentials(CLIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(CLIENT_ID))
                .verify();

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
    }

    /**
     * GET /clients fallisce con 500:
     * Atteso: {@link ExternalServiceException} con "KEYCLOAK" nel messaggio.
     */
    @Test
    void getPagopaClientCredentials_GetClientsFails_ThrowsExternalServiceException() {
        Queue<ClientResponse> responses = new LinkedList<>(List.of(serverError()));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).getPagopaClientCredentials(CLIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
    }

    /**
     * GET /clients ha successo ma GET /client-secret fallisce con 500:
     * Atteso: {@link ExternalServiceException}; entrambe le chiamate sono state eseguite.
     */
    @Test
    void getPagopaClientCredentials_FetchSecretFails_ThrowsExternalServiceException() {
        String clientsJson = "[{\"id\":\"" + INTERNAL_ID + "\",\"clientId\":\"" + CLIENT_ID + "\"}]";

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientsJson),  // GET /clients → trovato
                serverError()     // GET /clients/{INTERNAL_ID}/client-secret → 500
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).getPagopaClientCredentials(CLIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(1)).startsWith("GET").contains("/clients/" + INTERNAL_ID + "/client-secret");
    }

    /**
     * GET /client-secret risponde 200 ma senza il campo "value":
     * Atteso: {@link ExternalServiceException} per risposta malformata.
     */
    @Test
    void getPagopaClientCredentials_SecretValueMissing_ThrowsExternalServiceException() {
        String clientsJson     = "[{\"id\":\"" + INTERNAL_ID + "\",\"clientId\":\"" + CLIENT_ID + "\"}]";
        String malformedSecret = "{\"type\":\"secret\"}"; // campo "value" assente

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientsJson),
                ok(malformedSecret)
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).getPagopaClientCredentials(CLIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("fetchClientSecret"))
                .verify();
    }

    /**
     * Verifica che il {@code toString()} del DTO non esponga il {@code clientSecret} in chiaro.
     * Questo protegge da logging accidentale dell'oggetto (@{code log.info("{}", credentials)}).
     */
    @Test
    void tppPagopaCredentialsDto_ToString_MasksSecret() {
        TppPagopaCredentialsDTOV1 dto = new TppPagopaCredentialsDTOV1(CLIENT_ID, SECRET, GRANT_TYPE);

        String stringRepresentation = dto.toString();

        assertThat(stringRepresentation).doesNotContain(SECRET);
        assertThat(stringRepresentation).contains("***MASKED***");
        assertThat(stringRepresentation).contains(CLIENT_ID); // clientId visibile nei log è accettabile
    }

    /**
     * Verifica che il campo {@code grantType} del DTO sia sempre {@code "client_credentials"}
     * dopo la chiamata a {@code getPagopaClientCredentials}.
     */
    @Test
    void getPagopaClientCredentials_Success_GrantTypeIsClientCredentials() {
        String clientsJson = "[{\"id\":\"" + INTERNAL_ID + "\",\"clientId\":\"" + CLIENT_ID + "\"}]";
        String secretJson  = "{\"type\":\"secret\",\"value\":\"" + SECRET + "\"}";

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientsJson),
                ok(secretJson)
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).getPagopaClientCredentials(CLIENT_ID))
                .assertNext(credentials ->
                        assertThat(credentials.getGrantType()).isEqualTo("client_credentials"))
                .verifyComplete();
    }

    /**
     * Verifica che il {@code toString()} del DTO includa il campo {@code grantType} in chiaro
     * (non è un dato sensibile).
     */
    @Test
    void tppPagopaCredentialsDto_ToString_IncludesGrantType() {
        TppPagopaCredentialsDTOV1 dto = new TppPagopaCredentialsDTOV1(CLIENT_ID, SECRET, GRANT_TYPE);

        String stringRepresentation = dto.toString();

        assertThat(stringRepresentation).contains(GRANT_TYPE);
    }
}
