package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateIsPaymentEnabledDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateStateDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per i metodi di aggiornamento stato e pagamenti di {@link TppConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP senza avviare
 * server reali, garantendo la verifica della corretta costruzione degli URL, dei verbi e del mapping errori.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path aggiornamento stato — URL corretto, PUT e risposta mappata</li>
 *   <li>Aggiornamento stato fallito (404 Not Found) → {@link ResourceNotFoundException}</li>
 *   <li>Aggiornamento stato fallito (500 Error) → {@link ExternalServiceException}</li>
 *   <li>Happy path abilitazione pagamenti — URL corretto, PUT e successo (204)</li>
 *   <li>Abilitazione pagamenti fallita (404 Not Found) → {@link ResourceNotFoundException}</li>
 *   <li>Abilitazione pagamenti fallita (500 Error) → {@link ExternalServiceException}</li>
 * </ol>
 * </p>
 */
class TppConnectorImplTest {

    private static final String BASE_URL = "http://emd-tpp.test";
    private static final String TPP_ID = "tpp-12345";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse okJson(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private ClientResponse emptyResponse(HttpStatus status) {
        return ClientResponse.create(status).build();
    }

    private TppConnectorImpl connectorWith(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new TppConnectorImpl(builder, BASE_URL);
    }

    // ── Tests updateTppState ──────────────────────────────────────────────────

    /**
     * Happy path: aggiornamento stato TPP — verifica URL, HTTP PUT e deserializzazione della risposta.
     */
    @Test
    void updateTppState_Success() {
        String responseBody = "{\"tppId\": \"" + TPP_ID + "\", \"state\": true}";
        String[] capturedUrl = new String[1];
        String[] capturedMethod = new String[1];

        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            capturedMethod[0] = request.method().name();
            return Mono.just(okJson(responseBody));
        });

        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder()
                .tppId(TPP_ID)
                .state(true)
                .build();

        StepVerifier.create(connector.updateTppState(request))
                .assertNext(response -> {
                    assertThat(response.getTppId()).isEqualTo(TPP_ID);
                    assertThat(response.getState()).isTrue();
                })
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo("PUT");
        assertThat(capturedUrl[0]).endsWith("/emd/tpp");
    }

    /**
     * Aggiornamento stato fallito (404 Not Found) → {@link ResourceNotFoundException}
     */
    @Test
    void updateTppState_404_NotFound() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.NOT_FOUND)));
        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder().tppId(TPP_ID).state(true).build();

        StepVerifier.create(connector.updateTppState(request))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains(TPP_ID))
                .verify();
    }

    /**
     * Aggiornamento stato fallito (500 Error) → {@link ExternalServiceException}
     */
    @Test
    void updateTppState_500_Error() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.error(WebClientResponseException.create(500, "Internal Server Error", HttpHeaders.EMPTY, null, null)));
        
        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder().tppId(TPP_ID).state(true).build();

        StepVerifier.create(connector.updateTppState(request))
                .expectError(ExternalServiceException.class)
                .verify();
    }

    // ── Tests updateTppIsPaymentEnabled ───────────────────────────────────────

    /**
     * Happy path: aggiornamento abilitazione pagamenti — verifica URL dinamico con TPP_ID e HTTP PUT.
     */
    @Test
    void updateTppIsPaymentEnabled_Success() {
        String[] capturedUrl = new String[1];
        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(emptyResponse(HttpStatus.NO_CONTENT));
        });

        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder()
                .isPaymentEnabled(false)
                .build();

        StepVerifier.create(connector.updateTppIsPaymentEnabled(TPP_ID, request))
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/payment-enabled");
    }

    /**
     * Abilitazione pagamenti fallita (404 Not Found) → {@link ResourceNotFoundException}
     */
    @Test
    void updateTppIsPaymentEnabled_404_NotFound() {
        TppConnectorImpl connector = connectorWith(request -> Mono.just(emptyResponse(HttpStatus.NOT_FOUND)));
        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder().isPaymentEnabled(true).build();

        StepVerifier.create(connector.updateTppIsPaymentEnabled(TPP_ID, request))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException && ex.getMessage().contains(TPP_ID))
                .verify();
    }

    /**
     * Abilitazione pagamenti fallita (500 Error) → {@link ExternalServiceException}
     */
    @Test
    void updateTppIsPaymentEnabled_500_Error() {
        TppConnectorImpl connector = connectorWith(request -> 
            Mono.error(WebClientResponseException.create(500, "Internal Server Error", HttpHeaders.EMPTY, null, null)));
        
        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder().isPaymentEnabled(true).build();

        StepVerifier.create(connector.updateTppIsPaymentEnabled(TPP_ID, request))
                .expectError(ExternalServiceException.class)
                .verify();
    }
}