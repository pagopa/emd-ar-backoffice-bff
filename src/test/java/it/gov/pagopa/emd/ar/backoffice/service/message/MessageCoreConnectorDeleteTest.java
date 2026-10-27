package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests per il metodo {@code deleteMessageByEntityIdAndMessageId} di {@link MessageCoreConnectorImpl}.
 *
 * <p>Usa {@link ExchangeFunction} per intercettare le chiamate HTTP ed eseguire asserzioni
 * sull'URL costruito, il verbo HTTP utilizzato e la corretta mappatura degli errori.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Happy path: Chiamata DELETE formattata correttamente e risposta 204/200 OK</li>
 *   <li>Errore Not Found (404) -> Mappatura verso {@link ResourceNotFoundException}</li>
 *   <li>Errore Upstream (5xx) -> Mappatura verso {@link ExternalServiceException}</li>
 *   <li>Errore Client (4xx generico) -> Mappatura verso {@link ExternalServiceException}</li>
 * </ol>
 * </p>
 */
class MessageCoreConnectorDeleteTest {

    private static final String BASE_URL = "http://emd-message.test";

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Simula una risposta di successo senza corpo (es. 204 No Content).
     */
    private ClientResponse noContent() {
        return ClientResponse.create(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Simula una risposta di errore con un corpo testuale.
     */
    private ClientResponse errorResponse(HttpStatus status, String body) {
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
     * Happy path: Verifica che venga effettuata una DELETE verso l'URL corretto.
     */
    @Test
    void deleteMessage_HappyPath_CallsCorrectUrlAndCompletes() {
        String[] capturedUrl = new String[1];
        HttpMethod[] capturedMethod = new HttpMethod[1];

        MessageCoreConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            capturedMethod[0] = request.method();
            return Mono.just(noContent());
        });

        StepVerifier.create(connector.deleteMessageByEntityIdAndMessageId("ENT-123", "MSG-456"))
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo(HttpMethod.DELETE);
        assertThat(capturedUrl[0]).isEqualTo(BASE_URL + "/emd/message-core/ENT-123/MSG-456");
    }

    /**
     * Upstream 404 -> Mappatura verso ResourceNotFoundException con messaggio formattato.
     */
    @Test
    void deleteMessage_Upstream404_ThrowsResourceNotFoundException() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        MessageCoreConnectorImpl connector = connectorWith(request ->
                Mono.just(errorResponse(HttpStatus.NOT_FOUND, "Not found detail from server")));

        StepVerifier.create(connector.deleteMessageByEntityIdAndMessageId(entityId, messageId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException &&
                        ex.getMessage().contains("id MSG-456 for entity ENT-123"))
                .verify();
    }

    /**
     * Upstream 500 -> Mappatura verso ExternalServiceException.
     * Testa anche che la retry logic (se presente) non mascheri l'errore finale.
     */
    @Test
    void deleteMessage_Upstream500_ThrowsExternalServiceException() {
        MessageCoreConnectorImpl connector = connectorWith(request ->
                Mono.just(errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error")));

        StepVerifier.create(connector.deleteMessageByEntityIdAndMessageId("ENT-123", "MSG-456"))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                        ex.getMessage().contains("MESSAGE_SERVICE"))
                .verify();
    }

    /**
     * Upstream 400 (o qualsiasi 4xx che non sia 404) -> Fallback su ExternalServiceException.
     */
    @Test
    void deleteMessage_Upstream400_ThrowsExternalServiceException() {
        MessageCoreConnectorImpl connector = connectorWith(request ->
                Mono.just(errorResponse(HttpStatus.BAD_REQUEST, "Bad Request")));

        StepVerifier.create(connector.deleteMessageByEntityIdAndMessageId("ENT-123", "MSG-456"))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                        ex.getMessage().contains("MESSAGE_SERVICE"))
                .verify();
    }

    /**
     * Testa il recupero da un errore transitorio.
     * La prima chiamata fallisce con 503 (Service Unavailable), ma il retry
     * scatta e la seconda chiamata ha successo (204 No Content).
     */
    @Test
    void deleteMessage_TransientError_RetriesAndRecovers() {
        AtomicInteger requestCount = new AtomicInteger(0);

        MessageCoreConnectorImpl connector = connectorWith(request -> {
            int attempt = requestCount.incrementAndGet();
            if (attempt == 1) {
                return Mono.just(errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable"));
            }
            return Mono.just(noContent());
        });

        StepVerifier.create(connector.deleteMessageByEntityIdAndMessageId("ENT-123", "MSG-456"))
                .verifyComplete();

        assertThat(requestCount.get()).isEqualTo(2);
    }

    /**
     * Testa l'esaurimento dei retry.
     * Il servizio continua a restituire 502 Bad Gateway. Il sistema riprova,
     * ma alla fine esaurisce i tentativi e lancia ExternalServiceException.
     */
    @Test
    void deleteMessage_TransientError_RetriesAndEventuallyFails() {
        AtomicInteger requestCount = new AtomicInteger(0);

        MessageCoreConnectorImpl connector = connectorWith(request -> {
            requestCount.incrementAndGet();
            return Mono.just(errorResponse(HttpStatus.BAD_GATEWAY, "Bad Gateway"));
        });

        StepVerifier.create(connector.deleteMessageByEntityIdAndMessageId("ENT-123", "MSG-456"))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                        ex.getMessage().contains("MESSAGE_SERVICE"))
                .verify();

        assertThat(requestCount.get()).isGreaterThan(1);
    }
}