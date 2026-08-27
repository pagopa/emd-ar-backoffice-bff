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

    @Test
    void testAuthConnection_Success_CallsCorrectUri() throws InterruptedException {
        String tppId = "tpp-123";
        
        // Mock della risposta upstream
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"status\": \"OK\"}"));

        StepVerifier.create(tppConnector.testAuthConnection(tppId))
                .assertNext(result -> assertThat(result.get("status")).isEqualTo("OK"))
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).contains("/emd/tpp/network/connection/test");
        assertThat(recordedRequest.getPath()).contains("tppId=" + tppId);
    }

    @Test
    void testAuthConnection_UpstreamError_ThrowsExternalServiceException() {
        String tppId = "tpp-123";
        
        // Mock di un errore 500 dall'upstream
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        StepVerifier.create(tppConnector.testAuthConnection(tppId))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                    ((ExternalServiceException) ex).getHttpStatusCode() == 502)
                .verify();
    }
}