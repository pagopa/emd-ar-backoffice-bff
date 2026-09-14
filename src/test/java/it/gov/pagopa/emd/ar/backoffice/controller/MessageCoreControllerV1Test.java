package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.handler.ControllerExceptionHandler;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller.MessageCoreControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessageCoreControllerImplV1}.
 */
public class MessageCoreControllerV1Test {

    private MessageCoreService messageService;
    private WebTestClient webTestClient;
    private Utilities utilities;

    @BeforeEach
    void setUp() {
        messageService = Mockito.mock(MessageCoreService.class);
        utilities = Mockito.mock(Utilities.class);
        
        Mockito.lenient().when(utilities.getTraceId()).thenReturn("TEST-TRACE-ID");

        MessageCoreControllerImplV1 messageController = new MessageCoreControllerImplV1(messageService);
        
        webTestClient = WebTestClient.bindToController(messageController)
                .controllerAdvice(new ControllerExceptionHandler(utilities))
                .build();
    }
    
    // ── getMessageByEntityIdAndMessageId ─────────────────────────────────────────────────

    /**
     * GET /emd/message-core/{entityId}/{messageId} — happy path → 200 OK con restituzione del MessageDTOV1.
     */
    @Test
    void getMessageByEntityIdAndMessageId_Success() {
        String entityId = "entity-123";
        String messageId = "msg-123";
        MessageDTOV1 mockDto = new MessageDTOV1();
        
        when(messageService.getMessageByEntityIdAndMessageId(entityId, messageId))
                .thenReturn(Mono.just(mockDto));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(MessageDTOV1.class);

        verify(messageService, times(1)).getMessageByEntityIdAndMessageId(entityId, messageId);
    }

    /**
     * GET /emd/message-core/{entityId}/{messageId} — errore servizio esterno (es. 500) → lancia ExternalServiceException.
     */
    @Test
    void getMessageByEntityIdAndMessageId_ExternalServiceError() {
        String entityId = "entity-500";
        String messageId = "msg-500";
        when(messageService.getMessageByEntityIdAndMessageId(entityId, messageId))
                .thenReturn(Mono.error(new ExternalServiceException("MESSAGE_SERVICE", "getMessageByEntityIdAndMessageId", "Error from downstream")));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError();

        verify(messageService, times(1)).getMessageByEntityIdAndMessageId(entityId, messageId);
    }

    /**
     * GET /emd/message-core/{entityId}/{messageId} — message non trovato (404) → lancia ResourceNotFoundException.
     */
    @Test
    void getMessageByEntityIdAndMessageId_NotFound() {
        String entityId = "entity-404";
        String messageId = "msg-404";
        
        when(messageService.getMessageByEntityIdAndMessageId(entityId, messageId))
                .thenReturn(Mono.error(new ResourceNotFoundException("MESSAGE", messageId)));
                
        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
                
        verify(messageService, times(1)).getMessageByEntityIdAndMessageId(entityId, messageId);
    }

}
