package it.gov.pagopa.emd.ar.backoffice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;


public class MessageCoreConnectorGetMessageTest {

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

    /**
     * Happy path: 200 OK restituisce il DTO. Verifica che la URL chiamata sia corretta.
     */
    @Test
    void getMessageByMessageId_Success_ReturnsMessageDTO() {
        String messageId = "msg-123";
        String json = "{\"messageId\":\"msg-123\", \"status\":\"SENT\"}";
        String[] capturedUrl = new String[1];
        String[] capturedMethod = new String[1];

        MessageCoreConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            capturedMethod[0] = request.method().name();
            return Mono.just(okJson(json));
        });

        StepVerifier.create(connector.getMessageByMessageId(messageId))
                .expectNextMatches(dto -> dto != null)
                .verifyComplete();

        assertThat(capturedMethod[0]).isEqualTo("GET");
        assertThat(capturedUrl[0]).contains("/emd/message-core/" + messageId);
    }

    /**
     * Upstream 404 -> Mappatura verso ResourceNotFoundException per il BFF.
     */
    @Test
    void getMessageByMessageId_Upstream404_ThrowsResourceNotFoundException() {
        String messageId = "msg-404";
        String errorBody = "Message not found in database";

        MessageCoreConnectorImpl connector = connectorWith(request ->
                Mono.just(errorJson(HttpStatus.NOT_FOUND, errorBody)));

        StepVerifier.create(connector.getMessageByMessageId(messageId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException &&
                                            ex.getMessage().contains(messageId))
                .verify();
    }

    /**
     * Upstream 500 -> Mappa verso ExternalServiceException senza effettuare retry
     * (poiché l'errore custom bypassa le regole di transient network retry).
     */
    @Test
    void getMessageByMessageId_Upstream500_ThrowsExternalServiceException() {
        String messageId = "msg-500";
        String errorBody = "Internal Server Error";
        
        int[] requestCount = new int[1];

        MessageCoreConnectorImpl connector = connectorWith(request -> {
            requestCount[0]++;
            return Mono.just(errorJson(HttpStatus.INTERNAL_SERVER_ERROR, errorBody));
        });

        StepVerifier.create(connector.getMessageByMessageId(messageId))
                .expectError(ExternalServiceException.class)
                .verify();
        
        assertThat(requestCount[0]).isEqualTo(1);
    }

}
