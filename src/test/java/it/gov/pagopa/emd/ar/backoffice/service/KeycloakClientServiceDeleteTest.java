package it.gov.pagopa.emd.ar.backoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
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
 * Unit tests per il metodo {@code deleteKeycloakClient} di {@link KeycloakClientServiceImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP senza spinning up di server reali,
 * seguendo lo stesso pattern di {@link KeycloakUserServiceTest}.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path: client trovato e cancellato → Mono completes</li>
 *   <li>Client non trovato (lista vuota) → no-op, Mono completes senza errore</li>
 *   <li>GET /clients fallisce → errore propagato</li>
 *   <li>DELETE /clients/{id} fallisce → errore propagato</li>
 * </ol>
 * </p>
 */
class KeycloakClientServiceDeleteTest {

    private static final String BASE_URL    = "https://keycloak.test";
    private static final String REALM       = "test-realm";
    private static final String TPP_GROUP   = "tpp-group";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String CLIENT_ID   = "tpp-123";
    private static final String INTERNAL_ID = "kc-internal-uuid-001";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse ok(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private ClientResponse noContent() {
        return ClientResponse.create(HttpStatus.NO_CONTENT).body("").build();
    }

    private ClientResponse serverError() {
        return ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"server_error\",\"error_description\":\"Internal error\"}")
                .build();
    }

    /**
     * Costruisce il servizio con un {@link ExchangeFunction} iniettato e un token service mockato.
     */
    private KeycloakClientServiceImpl serviceWith(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        KeycloakTokenService tokenService = mock(KeycloakTokenService.class);
        when(tokenService.getManagerToken()).thenReturn(Mono.just(ADMIN_TOKEN));
        return new KeycloakClientServiceImpl(webClient, new ObjectMapper(), tokenService,
                BASE_URL, REALM, TPP_GROUP);
    }

    /**
     * Crea un {@link ExchangeFunction} che risponde con risposte da una FIFO queue
     * e registra ogni chiamata nella lista fornita.
     */
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
     * Happy path:
     *   1. GET /clients?clientId=tpp-123 → 200 [{id: INTERNAL_ID}]
     *   2. DELETE /clients/{INTERNAL_ID} → 204
     * Atteso: completa senza errori; entrambe le chiamate effettuate nell'ordine corretto.
     */
    @Test
    void deleteKeycloakClient_Success() {
        String clientsJson = "[{\"id\":\"" + INTERNAL_ID + "\",\"clientId\":\"" + CLIENT_ID + "\"}]";

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientsJson),  // GET /clients?clientId=...&exact=true
                noContent()       // DELETE /clients/{INTERNAL_ID}
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).deleteKeycloakClient(CLIENT_ID))
                .verifyComplete();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
        assertThat(calls.get(1)).startsWith("DELETE").contains("/clients/" + INTERNAL_ID);
    }

    /**
     * Client non trovato (GET restituisce lista vuota) → no-op, nessun errore.
     * La DELETE NON deve essere chiamata.
     */
    @Test
    void deleteKeycloakClient_ClientNotFound_NoOp() {
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok("[]")  // GET /clients → nessun client trovato
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).deleteKeycloakClient(CLIENT_ID))
                .verifyComplete();

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
    }

    /**
     * GET /clients fallisce con 500 → {@link ExternalServiceException} propagata.
     */
    @Test
    void deleteKeycloakClient_GetClientsFails_ErrorPropagated() {
        Queue<ClientResponse> responses = new LinkedList<>(List.of(serverError()));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).deleteKeycloakClient(CLIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).startsWith("GET");
    }

    /**
     * GET /clients ha successo ma DELETE fallisce con 500 → errore propagato.
     */
    @Test
    void deleteKeycloakClient_DeleteFails_ErrorPropagated() {
        String clientsJson = "[{\"id\":\"" + INTERNAL_ID + "\",\"clientId\":\"" + CLIENT_ID + "\"}]";

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientsJson),  // GET /clients → trovato
                serverError()     // DELETE /clients/{INTERNAL_ID} → 500
        ));
        List<String> calls = new CopyOnWriteArrayList<>();

        StepVerifier.create(serviceWith(queuedExchange(responses, calls)).deleteKeycloakClient(CLIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(1)).startsWith("DELETE").contains("/clients/" + INTERNAL_ID);
    }
}

