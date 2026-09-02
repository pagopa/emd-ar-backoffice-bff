package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import io.netty.handler.timeout.ReadTimeoutException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

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
     * Test errore 404: verifica che NON venga sollevata eccezione, 
     * ma restituito un DTO con status FAILURE e httpStatus 404.
     */
    @Test
    void testAuthConnection_404_NotFound_ReturnsFailureDTO() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.just(responseWithBody(HttpStatus.NOT_FOUND, "{\"error\":\"not_found\"}")));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("HTTP_ERROR");
                    assertThat(result.getHttpStatus()).isEqualTo(404);
                })
                .verifyComplete();
    }

    /**
     * Test errore 500: verifica il mapping in FAILURE DTO.
     */
    @Test
    void testAuthConnection_500_ServerError_ReturnsFailureDTO() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.just(responseWithBody(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error")));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("HTTP_ERROR");
                    assertThat(result.getHttpStatus()).isEqualTo(500);
                })
                .verifyComplete();
    }

    /**
     * Test Timeout: verifica che la ReadTimeoutException venga catturata 
     * e mappata in un DTO con status FAILURE e errorType TIMEOUT.
     */
    @Test
    void testAuthConnection_ReadTimeout_ReturnsTimeoutDTO() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.error(new ReadTimeoutException()));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("TIMEOUT");
                    assertThat(result.getHttpStatus()).isEqualTo(504);
                    assertThat(result.getDescription()).contains("ReadTimeout");
                })
                .verifyComplete();
    }

    /**
     * Test TimeoutException.
     */
    @Test
    void testAuthConnection_TimeoutException_ReturnsTimeoutDTO() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.error(ReadTimeoutException.INSTANCE)); 

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("TIMEOUT");
                })
                .verifyComplete();
    }

    /**
     * Test WebClientRequestException con causa ReadTimeoutException
     */
    @Test
    void testAuthConnection_WebClientRequestException_WithTimeoutCause() {
        // Creiamo la struttura WebClientRequestException -> causa ReadTimeoutException
        WebClientRequestException wrappedEx = new WebClientRequestException(
                new ReadTimeoutException(), HttpMethod.GET, URI.create(BASE_URL), 
                new HttpHeaders());

        TppConnectorImpl connector = connectorWith(request -> Mono.error(wrappedEx));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("TIMEOUT");
                })
                .verifyComplete();
    }

    /**
     * Test Errore di rete generico con messaggio.
     */
    @Test
    void testAuthConnection_GenericNetworkError_WithMessage() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.error(new RuntimeException("Connection refused")));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("NETWORK_ERROR");
                    assertThat(result.getDescription()).contains("Connection refused");
                })
                .verifyComplete();
    }

    /**
     * Test Errore di rete senza messaggio.
     */
    @Test
    void testAuthConnection_GenericNetworkError_NoMessage() {
        // RuntimeException senza messaggio
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.error(new RuntimeException())); 

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("NETWORK_ERROR");
                    // Verifichiamo che venga usato il nome della classe (SimpleName)
                    assertThat(result.getDescription()).contains("RuntimeException");
                })
                .verifyComplete();
    }

    @Test
    void testAuthConnection_ReactorTimeout_ReturnsTimeoutDTO() {
        // Simula l'eccezione lanciata dall'operatore .timeout() di Reactor
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.error(new java.util.concurrent.TimeoutException("Did not observe any item...")));

        StepVerifier.create(connector.testAuthConnection(TPP_ID))
                .assertNext(result -> {
                    assertThat(result.getStatus()).isEqualTo("FAILURE");
                    assertThat(result.getErrorType()).isEqualTo("TIMEOUT");
                    assertThat(result.getHttpStatus()).isEqualTo(504);
                })
                .verifyComplete();
    }

}