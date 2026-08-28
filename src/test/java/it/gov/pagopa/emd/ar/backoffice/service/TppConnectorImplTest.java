package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateIsPaymentEnabledDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateStateDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per TppConnectorImpl utilizzando MockWebServer (OkHttp3).
 */
class TppConnectorImplTest {

    private MockWebServer mockWebServer;
    private TppConnectorImpl tppConnector;
    private static final String TPP_ID = "tpp-12345";

    @BeforeEach
    void setUp() throws IOException {
        this.mockWebServer = new MockWebServer();
        this.mockWebServer.start();

        // Inizializziamo il connettore con l'URL dinamico del MockWebServer
        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient.Builder webClientBuilder = WebClient.builder();
        this.tppConnector = new TppConnectorImpl(webClientBuilder, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        this.mockWebServer.shutdown();
    }

    // ── Tests updateTppState ──────────────────────────────────────────────────

    @Test
    void updateTppState_Success() throws InterruptedException {
        // GIVEN: Una risposta 200 OK con corpo JSON
        String responseBody = "{\"tppId\": \"" + TPP_ID + "\", \"state\": true}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(responseBody));

        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder()
                .tppId(TPP_ID)
                .state(true)
                .build();

        // WHEN/THEN: Eseguiamo la chiamata e verifichiamo la risposta
        StepVerifier.create(tppConnector.updateTppState(request))
                .assertNext(response -> {
                    assertThat(response.getTppId()).isEqualTo(TPP_ID);
                    assertThat(response.getState()).isTrue();
                })
                .verifyComplete();

        // VERIFICA RICHIESTA: Ispezioniamo cosa è arrivato al server
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
        assertThat(recordedRequest.getPath()).isEqualTo("/emd/tpp");
        assertThat(recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE)).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(recordedRequest.getBody().readUtf8()).contains("\"tppId\":\"" + TPP_ID + "\"", "\"state\":true");
    }

    @Test
    void updateTppState_NotFound_ThrowsResourceNotFoundException() {
        // GIVEN: Il servizio risponde 404
        mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\":\"not found\"}"));

        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder().tppId(TPP_ID).state(true).build();

        // WHEN/THEN
        StepVerifier.create(tppConnector.updateTppState(request))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains(TPP_ID))
                .verify();
    }

    @Test
    void updateTppState_InternalError_ThrowsExternalServiceException() {
        // GIVEN: Il servizio risponde 500
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"internal error\"}"));

        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder().tppId(TPP_ID).state(true).build();

        // WHEN/THEN
        StepVerifier.create(tppConnector.updateTppState(request))
                .expectError(ExternalServiceException.class)
                .verify();
    }

    // ── Tests updateTppIsPaymentEnabled ───────────────────────────────────────

    @Test
    void updateTppIsPaymentEnabled_Success() throws InterruptedException {
        // GIVEN: Una risposta 204 No Content
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder()
                .isPaymentEnabled(false)
                .build();

        // WHEN/THEN
        StepVerifier.create(tppConnector.updateTppIsPaymentEnabled(TPP_ID, request))
                .verifyComplete();

        // VERIFICA RICHIESTA
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
        assertThat(recordedRequest.getPath()).isEqualTo("/emd/tpp/" + TPP_ID + "/payment-enabled");
        assertThat(recordedRequest.getBody().readUtf8()).contains("\"isPaymentEnabled\":false");
    }

    @Test
    void updateTppIsPaymentEnabled_NotFound_ThrowsResourceNotFoundException() {
        // GIVEN: 404 dal server
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder().isPaymentEnabled(true).build();

        // WHEN/THEN
        StepVerifier.create(tppConnector.updateTppIsPaymentEnabled(TPP_ID, request))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains(TPP_ID))
                .verify();
    }

    @Test
    void updateTppIsPaymentEnabled_ServerError_ThrowsExternalServiceException() {
        // GIVEN: 500 dal server
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder().isPaymentEnabled(true).build();

        // WHEN/THEN
        StepVerifier.create(tppConnector.updateTppIsPaymentEnabled(TPP_ID, request))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }
}