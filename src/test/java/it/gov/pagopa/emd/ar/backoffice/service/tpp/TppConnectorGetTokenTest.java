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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per il metodo {@code getTppToken} di {@link TppConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP senza spinning up
 * di server reali, seguendo lo stesso pattern degli altri test del progetto.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path: 200 con body completo → {@link TokenSection} deserializzato</li>
 *   <li>404 → {@link ResourceNotFoundException} con tppId nel messaggio</li>
 *   <li>500 → {@link ExternalServiceException}</li>
 *   <li>Correttezza URL chiamato: {@code GET /emd/tpp/{tppId}/token}</li>
 * </ol>
 * </p>
 */
class TppConnectorGetTokenTest {

    private static final String BASE_URL = "http://emd-tpp.test";
    private static final String TPP_ID   = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse okJson(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private ClientResponse errorJson(HttpStatus status) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"test error\"}")
                .build();
    }

    /**
     * Builds a {@link TppConnectorImpl} whose outbound HTTP calls are intercepted
     * by the supplied {@link ExchangeFunction} — no real server needed.
     */
    private TppConnectorImpl connectorWith(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new TppConnectorImpl(builder, BASE_URL);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy path: il connector chiama {@code GET /emd/tpp/{tppId}/token} e deserializza
     * correttamente la risposta in un {@link TokenSection} con tutti i campi popolati.
     */
    @Test
    void getTppToken_Success_ReturnsTokenSection() {
        String json = """
                {
                  "contentType": "application/json",
                  "pathAdditionalProperties": {"scope": "openid"},
                  "bodyAdditionalProperties": {"client_id": "my-client", "client_secret": "s3cr3t"}
                }
                """;

        String[] capturedUrl = new String[1];
        TppConnectorImpl c = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson(json));
        });

        StepVerifier.create(c.getTppToken(TPP_ID))
                .assertNext(token -> {
                    assertThat(token.getContentType()).isEqualTo("application/json");
                    assertThat(token.getPathAdditionalProperties())
                            .containsEntry("scope", "openid");
                    assertThat(token.getBodyAdditionalProperties())
                            .containsEntry("client_id", "my-client")
                            .containsEntry("client_secret", "s3cr3t");
                })
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("/emd/tpp/" + TPP_ID + "/token");
    }

    /**
     * 404 dal servizio emd-tpp: il connector converte in {@link ResourceNotFoundException}
     * con il {@code tppId} nel messaggio.
     */
    @Test
    void getTppToken_NotFound_ThrowsResourceNotFoundException() {
        TppConnectorImpl c = connectorWith(request -> Mono.just(errorJson(HttpStatus.NOT_FOUND)));

        StepVerifier.create(c.getTppToken(TPP_ID))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(TPP_ID))
                .verify();
    }

    /**
     * 500 dal servizio emd-tpp: il connector converte in {@link ExternalServiceException}.
     */
    @Test
    void getTppToken_ServerError_ThrowsExternalServiceException() {
        TppConnectorImpl c = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(c.getTppToken(TPP_ID))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }

    /**
     * Risposta con body parziale (solo contentType, mappe null):
     * il connector deve comunque deserializzare senza errori.
     */
    @Test
    void getTppToken_PartialBody_ReturnsTokenSectionWithNullMaps() {
        String json = "{\"contentType\": \"application/x-www-form-urlencoded\"}";

        TppConnectorImpl c = connectorWith(request -> Mono.just(okJson(json)));

        StepVerifier.create(c.getTppToken(TPP_ID))
                .assertNext(token -> {
                    assertThat(token.getContentType()).isEqualTo("application/x-www-form-urlencoded");
                    assertThat(token.getPathAdditionalProperties()).isNull();
                    assertThat(token.getBodyAdditionalProperties()).isNull();
                })
                .verifyComplete();
    }

    /**
     * Risposta con body vuoto ({}): i campi devono essere null, senza NPE.
     */
    @Test
    void getTppToken_EmptyBody_ReturnsEmptyTokenSection() {
        TppConnectorImpl c = connectorWith(request -> Mono.just(okJson("{}")));

        StepVerifier.create(c.getTppToken(TPP_ID))
                .assertNext(token -> {
                    assertThat(token.getContentType()).isNull();
                    assertThat(token.getBodyAdditionalProperties()).isNull();
                })
                .verifyComplete();
    }

    /**
     * Verifica il masking del DTO di risposta API:
     * {@code TokenSectionDTOV1.toString()} non deve esporre i valori delle mappe.
     * Il {@code contentType} è visibile (non sensibile), le chiavi delle mappe sono visibili,
     * ma i valori sono mascherati.
     */
    @Test
    void tokenSectionDtoV1_ToString_MasksMapValues() {
        it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1 dto =
                new it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1(
                        "application/json",
                        Map.of("scope", "openid"),
                        Map.of("client_secret", "supersecret", "password", "topsecret"));

        String str = dto.toString();

        // I valori segreti NON devono apparire
        assertThat(str).doesNotContain("supersecret");
        assertThat(str).doesNotContain("topsecret");
        // Il contentType è visibile
        assertThat(str).contains("application/json");
        // Le chiavi sono visibili (aiutano il debug senza esporre valori)
        assertThat(str).contains("client_secret");
        // Il marker di mascheramento è presente
        assertThat(str).contains("***MASKED***");
    }
}



