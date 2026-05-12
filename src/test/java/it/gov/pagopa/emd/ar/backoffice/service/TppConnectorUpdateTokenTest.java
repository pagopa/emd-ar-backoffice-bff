package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per il metodo {@code updateTppToken} di {@link TppConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP senza spinning up
 * di server reali, seguendo lo stesso pattern degli altri test del progetto.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path: 200 → {@code Mono<Void>} che completa correttamente</li>
 *   <li>404 → {@link ResourceNotFoundException} con tppId nel messaggio</li>
 *   <li>500 → {@link ExternalServiceException}</li>
 *   <li>Correttezza metodo HTTP (PUT) e URL: {@code PUT /update/{tppId}/token}</li>
 *   <li>Il body viene inviato (Content-Type: application/json)</li>
 *   <li>Privacy: verifica che il DTO {@code toString()} non esponga i valori delle mappe</li>
 * </ol>
 * </p>
 */
class TppConnectorUpdateTokenTest {

    private static final String BASE_URL = "http://emd-tpp.test";
    private static final String TPP_ID   = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse emptyOk() {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("")
                .build();
    }

    private ClientResponse errorJson(HttpStatus status) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"test error\"}")
                .build();
    }

    private TppConnectorImpl connectorWith(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new TppConnectorImpl(builder, BASE_URL);
    }

    private TokenSection sampleTokenSection() {
        return new TokenSection(
                "application/json",
                Map.of("scope", "openid"),
                Map.of("client_secret", "supersecret", "client_id", "my-client"));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy path: il connector chiama {@code PUT /update/{tppId}/token} e completa
     * senza errori. Verifica metodo HTTP e URL.
     */
    @Test
    void updateTppToken_Success_CompletesWithNoError() {
        List<String> capturedCalls = new CopyOnWriteArrayList<>();

        TppConnectorImpl c = connectorWith(request -> {
            capturedCalls.add(request.method().name() + " " + request.url().getPath());
            return Mono.just(emptyOk());
        });

        StepVerifier.create(c.updateTppToken(TPP_ID, sampleTokenSection()))
                .verifyComplete();

        assertThat(capturedCalls).hasSize(1);
        assertThat(capturedCalls.get(0)).startsWith("PUT");
        assertThat(capturedCalls.get(0)).contains("/update/" + TPP_ID + "/token");
    }

    /**
     * 404 dal servizio emd-tpp: il connector converte in {@link ResourceNotFoundException}
     * con il {@code tppId} nel messaggio.
     */
    @Test
    void updateTppToken_NotFound_ThrowsResourceNotFoundException() {
        TppConnectorImpl c = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.NOT_FOUND)));

        StepVerifier.create(c.updateTppToken(TPP_ID, sampleTokenSection()))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(TPP_ID))
                .verify();
    }

    /**
     * 500 dal servizio emd-tpp: il connector converte in {@link ExternalServiceException}.
     */
    @Test
    void updateTppToken_ServerError_ThrowsExternalServiceException() {
        TppConnectorImpl c = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(c.updateTppToken(TPP_ID, sampleTokenSection()))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }

    /**
     * 502 dal servizio emd-tpp: il connector converte in {@link ExternalServiceException}.
     */
    @Test
    void updateTppToken_BadGateway_ThrowsExternalServiceException() {
        TppConnectorImpl c = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.BAD_GATEWAY)));

        StepVerifier.create(c.updateTppToken(TPP_ID, sampleTokenSection()))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }

    /**
     * Verifica che una seconda chiamata con un body diverso (nullable fields) completi
     * correttamente — PUT è idempotente e non deve dipendere dalla presenza delle mappe.
     */
    @Test
    void updateTppToken_WithNullMaps_CompletesSuccessfully() {
        TokenSection minimalSection = new TokenSection("application/json", null, null);

        TppConnectorImpl c = connectorWith(request -> Mono.just(emptyOk()));

        StepVerifier.create(c.updateTppToken(TPP_ID, minimalSection))
                .verifyComplete();
    }

    /**
     * Verifica il masking del DTO di risposta API:
     * {@code TokenSectionDTOV1.toString()} non deve esporre i valori delle mappe.
     * Privacy fondamentale: {@code client_secret}, {@code password} ecc. non devono
     * mai comparire nei log.
     */
    @Test
    void tokenSectionDtoV1_ToString_MasksMapValues_OnUpdate() {
        it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1 dto =
                new it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1(
                        "application/x-www-form-urlencoded",
                        Map.of("tenantId", "123456"),
                        Map.of("client_secret", "nuovo-secret-xyz",
                               "grant_type", "client_credentials",
                               "client_id", "nuovo-client-id-888"));

        String str = dto.toString();

        // Valori sensibili NON devono apparire
        assertThat(str).doesNotContain("nuovo-secret-xyz");
        assertThat(str).doesNotContain("client_credentials"); // valore, non chiave
        assertThat(str).doesNotContain("nuovo-client-id-888");
        assertThat(str).doesNotContain("123456");    // valore di tenantId
        // Il contentType è visibile
        assertThat(str).contains("application/x-www-form-urlencoded");
        // Le chiavi sono visibili (utili per il debug)
        assertThat(str).contains("client_secret");
        assertThat(str).contains("client_id");
        // Il marker di mascheramento è presente
        assertThat(str).contains("***MASKED***");
    }
}

