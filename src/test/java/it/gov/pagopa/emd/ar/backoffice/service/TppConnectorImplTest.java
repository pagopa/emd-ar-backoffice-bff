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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per i metodi di aggiornamento stato di {@link TppConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP, garantendo la correttezza
 * degli URL chiamati, dei verbi HTTP (PUT) e della gestione dei codici di errore.</p>
 */
class TppConnectorUpdateTest {

    private static final String BASE_URL = "http://emd-tpp.test";
    private static final String TPP_ID   = "tpp-12345";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse okJson(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private ClientResponse noContent() {
        return ClientResponse.create(HttpStatus.NO_CONTENT).build();
    }

    private ClientResponse errorJson(HttpStatus status) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"upstream error\"}")
                .build();
    }

    private TppConnectorImpl connectorWith(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new TppConnectorImpl(builder, BASE_URL);
    }

    // ── Tests updateTppState ──────────────────────────────────────────────────

    /**
     * Verifica che {@code updateTppState} invii una PUT a /emd/tpp e restituisca
     * correttamente la risposta mappata.
     */
    @Test
    void updateTppState_Success_ReturnsResponse() {
        String jsonResponse = "{\"tppId\": \"" + TPP_ID + "\", \"state\": true}";
        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder().tppId(TPP_ID).state(true).build();
        
        String[] capturedUrl = new String[1];
        String[] capturedMethod = new String[1];

        TppConnectorImpl c = connectorWith(requestContext -> {
            capturedUrl[0] = requestContext.url().toString();
            capturedMethod[0] = requestContext.method().name();
            return Mono.just(okJson(jsonResponse));
        });

        StepVerifier.create(c.updateTppState(request))
                .assertNext(response -> {
                    assertThat(response.getTppId()).isEqualTo(TPP_ID);
                    assertThat(response.getState()).isTrue();
                })
                .verifyComplete();

        assertThat(capturedUrl[0]).isEqualTo(BASE_URL + "/emd/tpp");
        assertThat(capturedMethod[0]).isEqualTo("PUT");
    }

    @Test
    void updateTppState_NotFound_ThrowsResourceNotFoundException() {
        TppUpdateStateDTOV1 request = TppUpdateStateDTOV1.builder().tppId(TPP_ID).state(true).build();
        TppConnectorImpl c = connectorWith(req -> Mono.just(errorJson(HttpStatus.NOT_FOUND)));

        StepVerifier.create(c.updateTppState(request))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException 
                        && ex.getMessage().contains(TPP_ID))
                .verify();
    }

    // ── Tests updateTppIsPaymentEnabled ───────────────────────────────────────

    /**
     * Verifica che {@code updateTppIsPaymentEnabled} invii una PUT all'URL corretto
     * includendo il tppId nel path e restituisca Mono<Void> con 204.
     */
    @Test
    void updateTppIsPaymentEnabled_Success_Completes() {
        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder()
                .isPaymentEnabled(true).build();
        
        String[] capturedUrl = new String[1];

        TppConnectorImpl c = connectorWith(requestContext -> {
            capturedUrl[0] = requestContext.url().toString();
            return Mono.just(noContent());
        });

        StepVerifier.create(c.updateTppIsPaymentEnabled(TPP_ID, request))
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/payment-enabled");
    }

    /**
     * Verifica che un errore 500 dal servizio remoto venga mappato in ExternalServiceException.
     */
    @Test
    void updateTppIsPaymentEnabled_ServerError_ThrowsExternalServiceException() {
        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder()
                .isPaymentEnabled(true).build();

        TppConnectorImpl c = connectorWith(req -> Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(c.updateTppIsPaymentEnabled(TPP_ID, request))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException 
                        && ex.getMessage().contains("updateTppIsPaymentEnabled"))
                .verify();
    }

    /**
     * Verifica la gestione del 404 per l'abilitazione pagamenti.
     */
    @Test
    void updateTppIsPaymentEnabled_NotFound_ThrowsResourceNotFoundException() {
        TppUpdateIsPaymentEnabledDTOV1 request = TppUpdateIsPaymentEnabledDTOV1.builder()
                .isPaymentEnabled(true).build();

        TppConnectorImpl c = connectorWith(req -> Mono.just(errorJson(HttpStatus.NOT_FOUND)));

        StepVerifier.create(c.updateTppIsPaymentEnabled(TPP_ID, request))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException 
                        && ex.getMessage().contains(TPP_ID))
                .verify();
    }
}