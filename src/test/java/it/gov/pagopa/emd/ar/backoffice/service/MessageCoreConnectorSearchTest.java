package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests per il metodo {@code searchMessages} di {@link MessageCoreConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP ed eseguire asserzioni
 * sull'URL costruito (query parameters) e sulla deserializzazione della risposta.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path con ID filtri (messageId, recipientId, originId)</li>
 *   <li>Happy path con range temporale (startDate, endDate)</li>
 *   <li>Paginazione e campi (fields) come parametri multi-valore</li>
 *   <li>Gestione errori upstream: 400 (InvalidSearchField) e 5xx (ExternalService)</li>
 *   <li>Verifica filtri assenti (parametri non aggiunti all'URI)</li>
 * </ol>
 * </p>
 */
class MessageCoreConnectorSearchTest {

    private static final String BASE_URL = "http://emd-message.test";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse okJson(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private ClientResponse errorJson(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private MessageCoreConnectorImpl connectorWith(ExchangeFunction ef) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new MessageCoreConnectorImpl(builder, BASE_URL);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy path: ricerca con filtri ID. Verifica che l'URL contenga i parametri corretti.
     */
    @Test
    void searchMessages_WithIdFilters_ReturnsPagedResponse() {
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
        MessageCoreConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson(json));
        });

        StepVerifier.create(connector.searchMessages("MSG-1", "REC-1", "ORG-1", null, null, 0, 10, null))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("/emd/message-core/search");
        assertThat(capturedUrl[0]).contains("messageId=MSG-1");
        assertThat(capturedUrl[0]).contains("recipientId=REC-1");
        assertThat(capturedUrl[0]).contains("originId=ORG-1");
        assertThat(capturedUrl[0]).contains("page=0");
        assertThat(capturedUrl[0]).contains("size=10");
    }

    /**
     * Verifica il corretto inserimento delle date nell'URL.
     */
    @Test
    void searchMessages_WithDates_SendsDatesInUrl() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 31, 18, 0);
        
        String[] capturedUrl = new String[1];
        MessageCoreConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson("{\"content\":[]}"));
        });

        StepVerifier.create(connector.searchMessages(null, null, null, start, end, 0, 10, null))
                .expectNextCount(1)
                .verifyComplete();

        // Verifica che le date siano presenti nell'URL (formato ISO di default del toString di LocalDateTime)
        assertThat(capturedUrl[0]).contains("startDate=2026-10-01T10:00");
        assertThat(capturedUrl[0]).contains("endDate=2026-10-31T18:00");
    }

    /**
     * Verifica che i campi richiesti appaiano come parametri multi-valore (fields=A&fields=B).
     */
    @Test
    void searchMessages_WithFields_SendsMultiValueFields() {
        List<String> fields = List.of("id", "status");
        String[] capturedUrl = new String[1];
        MessageCoreConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson("{\"content\":[]}"));
        });

        StepVerifier.create(connector.searchMessages(null, null, null, null, null, 0, 10, fields))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("fields=id");
        assertThat(capturedUrl[0]).contains("fields=status");
    }

    /**
     * Verifica che i parametri null o vuoti non vengano aggiunti all'URI.
     */
    @Test
    void searchMessages_NoFilters_SendsOnlyPagination() {
        String[] capturedUrl = new String[1];
        MessageCoreConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(okJson("{\"content\":[]}"));
        });

        StepVerifier.create(connector.searchMessages("", "  ", null, null, null, 5, 50, null))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(capturedUrl[0]).doesNotContain("messageId");
        assertThat(capturedUrl[0]).doesNotContain("recipientId");
        assertThat(capturedUrl[0]).doesNotContain("originId");
        assertThat(capturedUrl[0]).contains("page=5");
        assertThat(capturedUrl[0]).contains("size=50");
    }

    /**
     * Upstream 400 -> Mappatura verso InvalidSearchFieldException.
     */
    @Test
    void searchMessages_Upstream400_ThrowsInvalidSearchFieldException() {
        String errorBody = "{\"code\":\"INVALID_SEARCH_FIELD\",\"description\":\"Invalid field 'dummy'\"}";
        MessageCoreConnectorImpl connector = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.BAD_REQUEST, errorBody)));

        StepVerifier.create(connector.searchMessages(null, null, null, null, null, 0, 10, List.of("dummy")))
                .expectErrorMatches(ex -> ex instanceof InvalidSearchFieldException &&
                                    ex.getMessage().contains("dummy"))
                .verify();
    }

    /**
     * Upstream 500 -> Mappatura verso ExternalServiceException.
     */
    @Test
    void searchMessages_Upstream500_ThrowsExternalServiceException() {
        MessageCoreConnectorImpl connector = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error")));

        StepVerifier.create(connector.searchMessages(null, null, null, null, null, 0, 10, null))
                .expectError(ExternalServiceException.class)
                .verify();
    }

    /**
     * Upstream 429 -> Mappatura verso ExternalServiceException.
     */
    @Test
    void searchMessages_Upstream429_ThrowsExternalServiceException() {
        MessageCoreConnectorImpl connector = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests")));

        StepVerifier.create(connector.searchMessages(null, null, null, null, null, 0, 10, null))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                                    ex.getMessage().contains("MESSAGE_SERVICE"))
                .verify();
    }
}