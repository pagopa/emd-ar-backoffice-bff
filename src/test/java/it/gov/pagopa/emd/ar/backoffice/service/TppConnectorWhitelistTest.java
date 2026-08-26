package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.RecipientIdOnWhitelistDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientAlreadyPresentException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per i metodi di gestione whitelist di {@link TppConnectorImpl}.
 *
 * <p>Usa {@link MockWebServer} per simulare il servizio remoto emd-tpp, garantendo la verifica 
 * della corretta costruzione degli URL, dei verbi HTTP e della serializzazione dei dati.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path inserimento whitelist — URL corretto, verbo POST e successo (2xx)</li>
 *   <li>Inserimento fallito (409 Conflict) → {@link RecipientAlreadyPresentException}</li>
 *   <li>Inserimento fallito (404 Not Found) → {@link ResourceNotFoundException}</li>
 *   <li>Inserimento fallito (500 Error) → {@link ExternalServiceException}</li>
 *   <li>Happy path rimozione whitelist — URL corretto, verbo DELETE e successo (2xx)</li>
 *   <li>Rimozione fallita (404 Not Found) con body "TPP_NOT_ONBOARDED" → {@link ResourceNotFoundException}</li>
 *   <li>Rimozione fallita (404 Not Found) con body "RECIPIENT_NOT_FOUND" → {@link RecipientNotFoundException}</li>
 *   <li>Rimozione fallita (404 Not Found) con body vuoto (Fallback) → {@link ResourceNotFoundException}</li>
 *   <li>Rimozione fallita (500 Error) → {@link ExternalServiceException}</li>
 *   <li>Happy path aggiornamento massivo whitelist — URL corretto, verbo PUT e successo (2xx)</li>
 *   <li>Aggiornamento fallito (404 Not Found) → {@link ResourceNotFoundException}</li>
 *   <li>Aggiornamento fallito (500 Error) → {@link ExternalServiceException}</li>
 * </ol>
 * </p>
 */
class TppConnectorWhitelistTest {

    private MockWebServer mockWebServer;
    private TppConnectorImpl tppConnector;

    private static final String TPP_ID = "tpp-123";
    private static final String RECIPIENT_ID = "rec-456";

    /**
     * Configurazione iniziale: avvia il server mock e istanzia il connettore con l'URL locale.
     */
    @BeforeEach
    void setUp() throws IOException {
        this.mockWebServer = new MockWebServer();
        this.mockWebServer.start();
        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        this.tppConnector = new TppConnectorImpl(WebClient.builder(), baseUrl);
    }

    /**
     * Pulizia post-test: spegne il server mock per liberare le risorse.
     */
    @AfterEach
    void tearDown() throws IOException {
        this.mockWebServer.shutdown();
    }

    // ── Tests per insertRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: inserimento in whitelist — verifica URL, verbo HTTP POST e completamento corretto.
     */
    @Test
    void insertRecipientIdOnWhitelist_Success() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(201));

        StepVerifier.create(tppConnector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .verifyComplete();

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/emd/tpp/" + TPP_ID + "/whitelist");
    }

    /**
     * Upstream 409 (Conflict) → {@link RecipientAlreadyPresentException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_409_Conflict() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(409).setBody("Conflict"));
        StepVerifier.create(tppConnector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectError(RecipientAlreadyPresentException.class).verify();
    }

    /**
     * Upstream 404 (Not Found) per TPP inesistente → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_404_NotFound() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));
        StepVerifier.create(tppConnector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectError(ResourceNotFoundException.class).verify();
    }

    /**
     * Upstream 500 (Internal Server Error) → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_500_ServerError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Error"));
        StepVerifier.create(tppConnector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectError(ExternalServiceException.class).verify();
    }

    // ── Tests per removeRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: rimozione da whitelist — verifica URL con recipientId, verbo HTTP DELETE e completamento corretto.
     */
    @Test
    void removeRecipientIdOnWhitelist_Success() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        StepVerifier.create(tppConnector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .verifyComplete();

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("DELETE");
        assertThat(req.getPath()).isEqualTo("/emd/tpp/" + TPP_ID + "/whitelist/" + RECIPIENT_ID);
    }

    /**
     * Upstream 404 con body "TPP_NOT_ONBOARDED" → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_404_TppNotOnboarded() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("{\"code\":\"TPP_NOT_ONBOARDED\"}"));
        StepVerifier.create(tppConnector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains("TPP"))
                .verify();
    }

    /**
     * Upstream 404 con body "RECIPIENT_NOT_FOUND" → {@link RecipientNotFoundException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_404_RecipientNotFound() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("{\"code\":\"RECIPIENT_NOT_FOUND\"}"));
        StepVerifier.create(tppConnector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectError(RecipientNotFoundException.class).verify();
    }

    /**
     * Upstream 404 con body vuoto → {@link ResourceNotFoundException} su elemento whitelist deve essere propagata (Fallback).
     */
    @Test
    void removeRecipientIdOnWhitelist_404_Fallback() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("")); 
        StepVerifier.create(tppConnector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains("Whitelist Element"))
                .verify();
    }

    /**
     * Upstream 500 (Internal Server Error) → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_500_ServerError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Error"));
        StepVerifier.create(tppConnector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectError(ExternalServiceException.class).verify();
    }

    // ── Tests per updateRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: aggiornamento massivo whitelist — verifica URL, verbo HTTP PUT e completamento corretto.
     */
    @Test
    void updateRecipientIdOnWhitelist_Success() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        StepVerifier.create(tppConnector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec1")))
                .verifyComplete();

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("PUT");
        assertThat(req.getPath()).isEqualTo("/emd/tpp/" + TPP_ID + "/whitelist");
    }

    /**
     * Upstream 404 (Not Found) per TPP inesistente → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void updateRecipientIdOnWhitelist_404_NotFound() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));
        StepVerifier.create(tppConnector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec1")))
                .expectError(ResourceNotFoundException.class).verify();
    }

    /**
     * Upstream 500 (Internal Server Error) → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void updateRecipientIdOnWhitelist_500_ServerError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Error"));
        StepVerifier.create(tppConnector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec1")))
                .expectError(ExternalServiceException.class).verify();
    }
}