package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TppConnectorImplTest {

    private static MockWebServer mockWebServer;
    private TppConnectorImpl tppConnector;

    @BeforeAll
    static void setUpAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient.Builder webClientBuilder = WebClient.builder();
        tppConnector = new TppConnectorImpl(webClientBuilder, baseUrl);
    }

    /**
     * Verifica che la chiamata al microservizio upstream avvenga all'URI corretto
     * e che il JSON ricevuto venga mappato correttamente nel DTO.
     */
    @Test
    void testAuthConnection_Success_CallsCorrectUri() throws InterruptedException {
        String tppId = "tpp-123";
        
        // Prepariamo un JSON che rispecchia la struttura di TppConnectionResponseDTOV1
        String jsonResponse = "{"
                + "\"status\": \"SUCCESS\","
                + "\"httpStatus\": 200,"
                + "\"description\": \"Test connection successful\""
                + "}";

        // Mock della risposta del microservizio upstream (emd-tpp)
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse));

        StepVerifier.create(tppConnector.testAuthConnection(tppId))
                .assertNext(result -> {
                    // Verifichiamo il mapping del DTO
                    assertThat(result.getStatus()).isEqualTo("SUCCESS");
                    assertThat(result.getHttpStatus()).isEqualTo(200);
                    assertThat(result.getDescription()).contains("successful");
                })
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        String expectedPath = "/emd/tpp/" + tppId + "/network/connection/test";
        assertThat(recordedRequest.getPath()).isEqualTo(expectedPath);
    }

    /**
     * Verifica che il connettore gestisca correttamente gli errori server (5xx)
     */
    @Test
    void testAuthConnection_UpstreamError_ThrowsExternalServiceException() {
        String tppId = "tpp-123";
        
        mockWebServer.enqueue(new MockResponse().setResponseCode(500)); // 1°
        mockWebServer.enqueue(new MockResponse().setResponseCode(500)); // 2°
        mockWebServer.enqueue(new MockResponse().setResponseCode(500)); // 3°

        StepVerifier.create(tppConnector.testAuthConnection(tppId))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();

        // Verify retry
        assertThat(mockWebServer.getRequestCount()).isGreaterThan(1);
}
}