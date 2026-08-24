package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.RecipientIdOnWhitelistDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientAlreadyPresentException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * server reali, seguendo lo stesso pattern degli altri test del progetto.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path inserimento whitelist — URL corretto, verbo POST e successo (2xx)</li>
 *   <li>Inserimento fallito (409 Conflict) → {@link RecipientAlreadyPresentException}</li>
 *   <li>Inserimento fallito (404 Not Found) → {@link ResourceNotFoundException}</li>
 *   <li>Inserimento fallito (500 Error) → {@link ExternalServiceException}</li>
 *   <li>Happy path rimozione whitelist — URL corretto, verbo DELETE e successo (2xx)</li>
 *   <li>Rimozione fallita (404 Not Found) → {@link RecipientNotFoundException}</li>
 *   <li>Rimozione fallita (500 Error) → {@link ExternalServiceException}</li>
 *   <li>Happy path aggiornamento massivo whitelist — URL corretto, verbo PUT e successo (2xx)</li>
 *   <li>Aggiornamento fallito (404 Not Found) → {@link ResourceNotFoundException}</li>
 *   <li>Aggiornamento fallito (500 Error) → {@link ExternalServiceException}</li>
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

    private ClientResponse errorJson(HttpStatus status) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"code\":\"TPP_GENERIC_ERROR\",\"description\":\"Something went wrong\"}")
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
    void insertRecipientIdOnWhitelist_HappyPath_ReturnsVoid() {
        String[] capturedUrl = new String[1];
        HttpMethod[] capturedMethod = new HttpMethod[1];
        
        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            capturedMethod[0] = request.method();
            return Mono.just(emptyResponse(HttpStatus.CREATED));
        });

        RecipientIdOnWhitelistDTOV1 dto = new RecipientIdOnWhitelistDTOV1();

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, dto))
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo(HttpMethod.POST);
        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/whitelist");
    }

    /**
     * Upstream 409 (Conflict) → {@link RecipientAlreadyPresentException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_Upstream409_ThrowsRecipientAlreadyPresentException() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(errorJson(HttpStatus.CONFLICT)));

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectErrorMatches(ex -> ex instanceof RecipientAlreadyPresentException)
                .verify();
    }

    /**
     * Upstream 404 (Not Found) per TPP inesistente → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_Upstream404_ThrowsResourceNotFoundException() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.NOT_FOUND)));

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException)
                .verify();
    }

    /**
     * Upstream 500 → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void insertRecipientIdOnWhitelist_Upstream500_ThrowsExternalServiceException() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(connector.insertRecipientIdOnWhitelist(TPP_ID, new RecipientIdOnWhitelistDTOV1()))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }

    // ── Tests per removeRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: rimozione da whitelist — verifica URL con recipientId, verbo HTTP DELETE e completamento corretto.
     */
    @Test
    void removeRecipientIdOnWhitelist_HappyPath_ReturnsVoid() {
        String[] capturedUrl = new String[1];
        HttpMethod[] capturedMethod = new HttpMethod[1];

        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            capturedMethod[0] = request.method();
            return Mono.just(emptyResponse(HttpStatus.NO_CONTENT));
        });

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo(HttpMethod.DELETE);
        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/whitelist/" + RECIPIENT_ID);
    }

    /**
     * Upstream 404 con body "RECIPIENT_NOT_FOUND" → {@link RecipientNotFoundException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_Upstream404_RecipientNotFound_ThrowsRecipientNotFoundException() {
        ClientResponse response404 = ClientResponse.create(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"code\":\"RECIPIENT_NOT_FOUND\",\"description\":\"Recipient is missing\"}")
                .build();
        
        TppConnectorImpl connector = connectorWith(request -> Mono.just(response404));

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectErrorMatches(ex -> ex instanceof RecipientNotFoundException)
                .verify();
    }

    /**
     * Upstream 404 con body "TPP_NOT_ONBOARDED" → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_Upstream404_TppNotFound_ThrowsResourceNotFoundException() {
        ClientResponse response404 = ClientResponse.create(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"code\":\"TPP_NOT_ONBOARDED\",\"description\":\"TPP not found\"}")
                .build();

        TppConnectorImpl connector = connectorWith(request -> Mono.just(response404));

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException)
                .verify();
    }

    /**
     * Upstream 500 → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void removeRecipientIdOnWhitelist_Upstream500_ThrowsExternalServiceException() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(connector.removeRecipientIdOnWhitelist(TPP_ID, RECIPIENT_ID))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }

    // ── Tests per updateRecipientIdOnWhitelist ───────────────────────────────

    /**
     * Happy path: aggiornamento massivo whitelist — verifica URL, verbo HTTP PUT e completamento corretto.
     */
    @Test
    void updateRecipientIdOnWhitelist_HappyPath_ReturnsVoid() {
        String[] capturedUrl = new String[1];
        HttpMethod[] capturedMethod = new HttpMethod[1];

        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            capturedMethod[0] = request.method();
            return Mono.just(emptyResponse(HttpStatus.NO_CONTENT));
        });

        List<String> recipientIds = List.of("rec-1", "rec-2");

        StepVerifier.create(connector.updateRecipientIdOnWhitelist(TPP_ID, recipientIds))
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo(HttpMethod.PUT);
        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/whitelist");
    }

    /**
     * Upstream 404 (Not Found) per TPP inesistente → {@link ResourceNotFoundException} deve essere propagata.
     */
    @Test
    void updateRecipientIdOnWhitelist_Upstream404_ThrowsResourceNotFoundException() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.NOT_FOUND)));

        StepVerifier.create(connector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec-1")))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException)
                .verify();
    }

    /**
     * Upstream 500 → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void updateRecipientIdOnWhitelist_Upstream500_ThrowsExternalServiceException() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(connector.updateRecipientIdOnWhitelist(TPP_ID, List.of("rec-1")))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }
}