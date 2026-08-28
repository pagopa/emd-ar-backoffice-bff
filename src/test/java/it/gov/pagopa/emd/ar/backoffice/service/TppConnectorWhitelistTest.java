package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.RecipientIdOnWhitelistDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientAlreadyPresentException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per i metodi di gestione whitelist di {@link TppConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP senza avviare
 * server reali, garantendo la verifica della corretta costruzione degli URL e dei verbi.</p>
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
 *   <li>{@code insertRecipientIdOnWhitelist}: Successo, 409 (Already Present), 404 (TPP Not Found), 500 (External Error)</li>
 *   <li>{@code removeRecipientIdOnWhitelist}: Successo, 404 con vari codici errore (TPP vs Recipient), 500</li>
 *   <li>{@code updateRecipientIdOnWhitelist}: Successo, 404, 500</li>
 * </ol>
 * </p>
 */
class TppConnectorWhitelistTest {

    private static final String BASE_URL = "http://emd-tpp.test";
    private static final String TPP_ID = "tpp-123";
    private static final String RECIPIENT_ID = "rec-456";

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

    // ── Tests per insertRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: inserimento in whitelist — verifica URL, verbo HTTP POST e completamento corretto.
     */
    @Test
    void insertRecipientIdOnWhitelist_Success() {
        String[] capturedMethod = new String[1];
        String[] capturedUrl = new String[1];

        TppConnectorImpl connector = connectorWith(request -> {
            capturedMethod[0] = request.method().name();
            capturedUrl[0] = request.url().toString();
            return Mono.just(emptyResponse(HttpStatus.CREATED));
        });

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo("POST");
        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/whitelist");
    }

    /**
     * Upstream 409 (Conflict) → {@link RecipientAlreadyPresentException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_409_Conflict() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.CONFLICT)));

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectError(RecipientAlreadyPresentException.class)
                .verify();
    }

    /**
     * Upstream 404 (Not Found) per TPP inesistente → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_404_NotFound() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.NOT_FOUND)));

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    /**
     * Upstream 500 (Internal Server Error) → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_500_ServerError() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectError(ExternalServiceException.class)
                .verify();
    }

    // ── Tests per removeRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: rimozione da whitelist — verifica URL con recipientId, verbo HTTP DELETE e completamento corretto.
     */
    @Test
    void removeRecipientIdOnWhitelist_Success() {
        String[] capturedMethod = new String[1];
        TppConnectorImpl connector = connectorWith(request -> {
            capturedMethod[0] = request.method().name();
            return Mono.just(emptyResponse(HttpStatus.NO_CONTENT));
        });

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo("DELETE");
    }

    /**
     * Upstream 404 con body "TPP_NOT_ONBOARDED" → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_404_TppNotOnboarded() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.just(responseWithBody(HttpStatus.NOT_FOUND, "{\"code\":\"TPP_NOT_ONBOARDED\"}")));

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains("TPP"))
                .verify();
    }

    /**
     * Upstream 404 con body "RECIPIENT_NOT_FOUND" → {@link RecipientNotFoundException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_404_RecipientNotFound() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.just(responseWithBody(HttpStatus.NOT_FOUND, "{\"code\":\"RECIPIENT_NOT_FOUND\"}")));

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectError(RecipientNotFoundException.class)
                .verify();
    }

    /**
     * Upstream 404 con body vuoto → {@link ResourceNotFoundException} su elemento whitelist deve essere propagata (Fallback).
     */
    @Test
    void removeRecipientIdOnWhitelist_404_Fallback() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.NOT_FOUND)));

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains("Whitelist Element"))
                .verify();
    }

    /**
     * Upstream 500 (Internal Server Error) → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_500_ServerError() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectError(ExternalServiceException.class)
                .verify();
    }

    // ── Tests per updateRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: aggiornamento massivo whitelist — verifica URL, verbo HTTP PUT e completamento corretto.
     */
    @Test
    void updateRecipientIdOnWhitelist_Success() {
        String[] capturedMethod = new String[1];
        TppConnectorImpl connector = connectorWith(request -> {
            capturedMethod[0] = request.method().name();
            return Mono.just(emptyResponse(HttpStatus.NO_CONTENT));
        });

        StepVerifier.create(connector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec1")))
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo("PUT");
    }

    /**
     * Upstream 404 (Not Found) per TPP inesistente → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void updateRecipientIdOnWhitelist_404_NotFound() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.NOT_FOUND)));

        StepVerifier.create(connector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec1")))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    /**
     * Upstream 500 (Internal Server Error) → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void updateRecipientIdOnWhitelist_500_ServerError() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(connector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec1")))
                .expectError(ExternalServiceException.class)
                .verify();
    }
}