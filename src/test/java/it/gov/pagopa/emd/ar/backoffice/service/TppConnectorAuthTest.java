package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

/**
 * Unit tests per il metodo testAuthConnection di {@link TppConnectorImpl}.
 */
class TppConnectorAuthTest {

    private static final String BASE_URL = "http://emd-tpp.test";
    private static final String TPP_ID = "tpp-123";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse emptyResponse(HttpStatus status) {
        return ClientResponse.create(status).build();
    }

    private ClientResponse responseWithBody(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private TppConnectorImpl connectorWith(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new TppConnectorImpl(builder, BASE_URL);
    }

    // ── Tests per testAuthConnection ─────────────────────────────────────────

    /**
     * Test di successo: verifica URL, metodo GET e corretto mapping del DTO di risposta.
     */
    @Test
    void testAuthConnection_Success() {
        String[] capturedMethod = new String[1];
        String[] capturedUrl = new String[1];

        String jsonResponse = "{"
                + "\"status\": \"SUCCESS\","
                + "\"httpStatus\": 200,"
                + "\"description\": \"Test connection successful\""
                + "}";

        TppConnectorImpl connector = connectorWith(request -> {
            capturedMethod[0] = request.method().name();
            capturedUrl[0] = request.url().toString();
            return Mono.just(responseWithBody(HttpStatus.OK, jsonResponse));
        });

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("SUCCESS");
                    assertThat(result.getHttpStatus()).isEqualTo(200);
                    assertThat(result.getDescription()).contains("successful");
                })
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo("GET");
        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/network/connection/test");
    }

    /**
     * Test errore 404: verifica che venga sollevata l'eccezione ExternalServiceException.
     */
    @Test
    void testAuthConnection_404_NotFound_ThrowsExternalServiceException() {
        String errorBody = "{\"error\":\"not_found\"}";
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.just(responseWithBody(HttpStatus.NOT_FOUND, errorBody)));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(ExternalServiceException.class);
                    // Il messaggio contiene il prefisso loggato e il body ricevuto
                    assertThat(ex.getMessage()).contains("[TPP_SERVICE][testAuthConnection]");
                    assertThat(ex.getMessage()).contains(errorBody);
                })
                .verify();
    }

    /**
     * Test errore 500: verifica che venga sollevata l'eccezione ExternalServiceException 
     * con il dettaglio dell'errore.
     */
    @Test
    void testAuthConnection_500_ServerError() {
        String errorBody = "Upstream connection timed out";
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.just(responseWithBody(HttpStatus.INTERNAL_SERVER_ERROR, errorBody)));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(ExternalServiceException.class);
                    assertThat(ex.getMessage()).contains(errorBody);
                })
                .verify();
    }

    /**
     * Test di resilienza/retry: simulazione di un singolo fallimento seguito da un successo.
     */
    @Test
    void testAuthConnection_Retry_SuccessOnSecondAttempt() {
        int[] attempts = {0};
        
        TppConnectorImpl connector = connectorWith(request -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                // Simula un errore 503 (transitorio)
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
            }
            return Mono.just(responseWithBody(HttpStatus.OK, "{\"status\":\"SUCCESS\"}"));
        });

        // withVirtualTime permette di saltare i tempi di attesa del retry (backoff)
        StepVerifier.withVirtualTime(() -> connector.testAuthConnection(TPP_ID))
                .thenAwait(Duration.ofSeconds(10)) // Aspetta virtualmente il tempo del retry
                .assertNext(res -> assertThat(res.getStatus()).isEqualTo("SUCCESS"))
                .verifyComplete();

        assertThat(attempts[0]).isGreaterThan(1);
    }
}