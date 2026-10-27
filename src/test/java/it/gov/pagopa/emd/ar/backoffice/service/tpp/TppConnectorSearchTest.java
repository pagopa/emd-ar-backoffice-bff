package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException;
import java.util.List;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
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
 * Unit tests per il metodo {@code searchTpp} di {@link TppConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP senza avviare
 * server reali, seguendo lo stesso pattern degli altri test del progetto.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path con entityId — URL corretto e risposta deserializzata</li>
 *   <li>Happy path con businessName — parametro presente in query string</li>
 *   <li>Risposta con content vuoto (pagina vuota)</li>
 *   <li>Upstream 400 INVALID_SEARCH_FIELD → {@link InvalidSearchFieldException}</li>
 *   <li>Upstream 400 generico → {@link InvalidSearchFieldException}</li>
 *   <li>Upstream 500 → {@link ExternalServiceException}</li>
 *   <li>Upstream 429 → {@link ExternalServiceException}</li>
 *   <li>Nessun filtro — i parametri entityId e businessName assenti dal URI</li>
 *   <li>Fields validi — parametri fields presenti nell'URI come multi-valore</li>
 *   <li>Risposta multi-pagina — metadati di paginazione corretti</li>
 * </ol>
 * </p>
 */
class TppConnectorSearchTest {

    private static final String BASE_URL = "http://emd-tpp.test";

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
                .body("{\"code\":\"TPP_GENERIC_ERROR\",\"description\":\"Something went wrong\"}")
                .build();
    }

    private TppConnectorImpl connectorWith(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new TppConnectorImpl(builder, BASE_URL);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy path: ricerca per entityId — verifica URL, deserializzazione e campi di paginazione.
     */
    @Test
    void searchTpp_ByEntityId_ReturnsPagedResponse() {
        String json = """
                {
                  "content": [
                    {
                      "tppId": "tpp-001",
                      "entityId": "04256050875",
                      "businessName": "Acme TPP S.r.l.",
                      "state": true
                    }
                  ],
                  "page": 0,
                  "size": 10,
                  "totalElements": 1,
                  "totalPages": 1
                }
                """;

        String[] capturedUrl = new String[1];
        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson(json));
        });

        StepVerifier.create(connector.searchTpp("04256050875", null, 0, 10, null))
                .assertNext(response -> {
                    assertThat(response.getTotalElements()).isEqualTo(1);
                    assertThat(response.getTotalPages()).isEqualTo(1);
                    assertThat(response.getPage()).isEqualTo(0);
                    assertThat(response.getSize()).isEqualTo(10);
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().get(0).getTppId()).isEqualTo("tpp-001");
                    assertThat(response.getContent().get(0).getBusinessName()).isEqualTo("Acme TPP S.r.l.");
                })
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("/emd/tpp/search");
        assertThat(capturedUrl[0]).contains("entityId=04256050875");
        assertThat(capturedUrl[0]).contains("page=0");
        assertThat(capturedUrl[0]).contains("size=10");
        assertThat(capturedUrl[0]).doesNotContain("businessName");
        assertThat(capturedUrl[0]).doesNotContain("fields");
    }

    /**
     * Happy path: ricerca per businessName — verifica che il param appaia nell'URL
     * e che entityId sia assente.
     */
    @Test
    void searchTpp_ByBusinessName_SendsCorrectQueryParam() {
        String json = """
                {
                  "content": [],
                  "page": 0,
                  "size": 20,
                  "totalElements": 0,
                  "totalPages": 0
                }
                """;

        String[] capturedUrl = new String[1];
        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson(json));
        });

        StepVerifier.create(connector.searchTpp(null, "MDC", 0, 20, null))
                .assertNext(response -> assertThat(response.getTotalElements()).isEqualTo(0))
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("businessName=MDC");
        assertThat(capturedUrl[0]).doesNotContain("entityId");
        assertThat(capturedUrl[0]).doesNotContain("fields");
    }

    /**
     * Risposta con lista vuota: nessun elemento, ma la paginazione è corretta.
     */
    @Test
    void searchTpp_EmptyResult_ReturnsZeroElements() {
        String json = """
                {
                  "content": [],
                  "page": 2,
                  "size": 10,
                  "totalElements": 0,
                  "totalPages": 0
                }
                """;

        TppConnectorImpl connector = connectorWith(request -> Mono.just(okJson(json)));

        StepVerifier.create(connector.searchTpp("99999999999", null, 2, 10, null))
                .assertNext(response -> {
                    assertThat(response.getContent()).isEmpty();
                    assertThat(response.getTotalElements()).isEqualTo(0);
                    assertThat(response.getPage()).isEqualTo(2);
                })
                .verifyComplete();
    }

    /**
     * Upstream 400 → {@link InvalidSearchFieldException} deve essere propagata.
     */
    @Test
    void searchTpp_Upstream400_ThrowsInvalidSearchFieldException() {
        TppConnectorImpl connector = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.BAD_REQUEST)));

        StepVerifier.create(connector.searchTpp("bad-input", null, 0, 10, null))
                .expectErrorMatches(ex -> ex instanceof InvalidSearchFieldException)
                .verify();
    }

    /**
     * Upstream 400 con fields invalidi → {@link InvalidSearchFieldException} con il campo nel messaggio.
     */
    @Test
    void searchTpp_Upstream400WithInvalidFields_ThrowsInvalidSearchFieldExceptionWithField() {
        ClientResponse badFieldResponse = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"code\":\"INVALID_SEARCH_FIELD\",\"description\":\"Field 'invalidField' is not allowed\"}")
                .build();

        TppConnectorImpl connector = connectorWith(request -> Mono.just(badFieldResponse));

        StepVerifier.create(connector.searchTpp(null, "ACME", 0, 10, List.of("invalidField")))
                .expectErrorMatches(ex -> ex instanceof InvalidSearchFieldException
                        && ex.getMessage().contains("invalidField"))
                .verify();
    }

    /**
     * Upstream 500 → {@link ExternalServiceException} deve essere propagata.
     */
    @Test
    void searchTpp_Upstream500_ThrowsExternalServiceException() {
        TppConnectorImpl connector = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR)));

        StepVerifier.create(connector.searchTpp("04256050875", null, 0, 10, null))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }

    /**
     * Upstream 429 (Too Many Requests) → {@link ExternalServiceException}.
     */
    @Test
    void searchTpp_Upstream429_ThrowsExternalServiceException() {
        TppConnectorImpl connector = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.TOO_MANY_REQUESTS)));

        StepVerifier.create(connector.searchTpp(null, "ACME", 0, 10, null))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException)
                .verify();
    }

    /**
     * Senza filtri: né entityId né businessName vengono aggiunti al URI.
     * Vengono trasmessi solo page e size.
     */
    @Test
    void searchTpp_NoFilters_SendsOnlyPaginationParams() {
        String json = """
                {
                  "content": [],
                  "page": 0,
                  "size": 10,
                  "totalElements": 0,
                  "totalPages": 0
                }
                """;

        String[] capturedUrl = new String[1];
        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson(json));
        });

        StepVerifier.create(connector.searchTpp(null, null, 0, 10, null))
                .assertNext(response -> assertThat(response).isNotNull())
                .verifyComplete();

        assertThat(capturedUrl[0]).doesNotContain("entityId");
        assertThat(capturedUrl[0]).doesNotContain("businessName");
        assertThat(capturedUrl[0]).doesNotContain("fields");
        assertThat(capturedUrl[0]).contains("page=0");
        assertThat(capturedUrl[0]).contains("size=10");
    }

    /**
     * Fields validi: i parametri fields appaiono come multi-valore nell'URI.
     */
    @Test
    void searchTpp_WithFields_SendsFieldsInUrl() {
        String json = """
                {
                  "content": [{"tppId": "tpp-001", "businessName": "Acme"}],
                  "page": 0,
                  "size": 10,
                  "totalElements": 1,
                  "totalPages": 1
                }
                """;

        String[] capturedUrl = new String[1];
        TppConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson(json));
        });

        StepVerifier.create(connector.searchTpp(null, "Acme", 0, 10, List.of("tppId", "businessName")))
                .assertNext(response -> assertThat(response.getContent()).hasSize(1))
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("fields=tppId");
        assertThat(capturedUrl[0]).contains("fields=businessName");
    }

    /**
     * Risposta con paginazione multipagina: verifica i metadati.
     */
    @Test
    void searchTpp_MultiPage_ReturnsPaginationMetadata() {
        String json = """
                {
                  "content": [
                    {"tppId": "tpp-001", "businessName": "Rossi S.r.l."},
                    {"tppId": "tpp-002", "businessName": "Bianchi S.p.A."}
                  ],
                  "page": 1,
                  "size": 2,
                  "totalElements": 137,
                  "totalPages": 69
                }
                """;

        TppConnectorImpl connector = connectorWith(request -> Mono.just(okJson(json)));

        StepVerifier.create(connector.searchTpp(null, "S", 1, 2, null))
                .assertNext(response -> {
                    assertThat(response.getPage()).isEqualTo(1);
                    assertThat(response.getSize()).isEqualTo(2);
                    assertThat(response.getTotalElements()).isEqualTo(137);
                    assertThat(response.getTotalPages()).isEqualTo(69);
                    assertThat(response.getContent()).hasSize(2);
                })
                .verifyComplete();
    }

}

