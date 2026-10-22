package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller.MessageCoreControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageStateV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.handler.ControllerExceptionHandler;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchParameterV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.*;
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
        MessageCoreControllerImplV1 messageController = new MessageCoreControllerImplV1(messageService);

        utilities = Mockito.mock(Utilities.class);
        
        Mockito.lenient().when(utilities.getTraceId()).thenReturn("TEST-TRACE-ID");
        
        webTestClient = WebTestClient.bindToController(messageController)
                .controllerAdvice(new ControllerExceptionHandler(utilities))
                .build();
    }

    // ── searchMessages ────────────────────────────────────────────────────────

    /**
     * GET /emd/backoffice/api/v1/message-core/search — happy path with filters → 200 con lista e paginazione.
     */
    @Test
    void searchMessages_WithFilters_Returns200WithPagedResult() {
        MessageSearchResponseDTOV1 response = MessageSearchResponseDTOV1.builder()
                .content(java.util.List.of())
                .size(10)
                .totalElements(0L)
                .totalPages(0)
                .nextCursor("next-page-cursor")
                .build();

        when(messageService.searchMessages(any(MessageSearchParameterV1.class)))
            .thenReturn(Mono.just(response));

        // When
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/emd/backoffice/api/v1/message-core/search")
                        .queryParam("messageId", "MSG-123")
                        .queryParam("recipientId", "REC-456")
                        .queryParam("originId", "ORG-789")
                        .queryParam("state", "SENT")
                        .queryParam("startDate", "2026-10-01T10:00:00")
                        .queryParam("endDate", "2026-10-31T10:00:00")
                        .queryParam("cursor", "cursor")
                        .queryParam("size", 10)
                        .queryParam("fields", "id", "status")
                        .build())
                .exchange()
                // Then
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.nextCursor").isEqualTo("next-page-cursor");
        
        ArgumentCaptor<MessageSearchParameterV1> captor = ArgumentCaptor.forClass(MessageSearchParameterV1.class);
        Mockito.verify(messageService).searchMessages(captor.capture());
        
        MessageSearchParameterV1 captured = captor.getValue();
        Assertions.assertEquals("MSG-123", captured.getMessageId());
        Assertions.assertEquals(MessageStateV1.SENT, captured.getState());
        Assertions.assertEquals(10, captured.getSize());
        Assertions.assertTrue(captured.getFields().contains("id"));
    }

    /**
     * GET /emd/backoffice/api/v1/message-core/search — no filters → 200 con parametri default.
     */
    @Test
    void searchMessages_NoFilters_Returns200WithDefaults() {
        MessageSearchResponseDTOV1 response = MessageSearchResponseDTOV1.builder()
                .size(10)
                .build();

        when(messageService.searchMessages(any(MessageSearchParameterV1.class)))
                .thenReturn(Mono.just(response));

        // When
        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/search")
                .exchange()
                // Then
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.nextCursor").doesNotExist();

        ArgumentCaptor<MessageSearchParameterV1> captor = ArgumentCaptor.forClass(MessageSearchParameterV1.class);
        Mockito.verify(messageService).searchMessages(captor.capture());
        Assertions.assertEquals(10, captor.getValue().getSize());
        Assertions.assertNull(captor.getValue().getMessageId());
    }

    /**
     * GET /emd/backoffice/api/v1/message-core/search — service error → 502 Bad Gateway.
     */
    @Test
    void searchMessages_ServiceError_Returns502() {
        // Given
        when(messageService.searchMessages(any(MessageSearchParameterV1.class)))
                .thenReturn(Mono.error(new it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException(
                        "MESSAGE_SERVICE", "searchMessages", "Upstream error")));

        // When
        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/search")
                .exchange()
                // Then
                .expectStatus().is5xxServerError();
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

    // ── deleteMessage ────────────────────────────────────────────────────────

    /**
     * DELETE /emd/backoffice/api/v1/message-core/{entityId}/{messageId} — happy path → 204 No Content.
     */
    @Test
    void deleteMessage_HappyPath_Returns204NoContent() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        when(messageService.deleteMessage(entityId, messageId))
                .thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
        
        Mockito.verify(messageService).deleteMessage(entityId, messageId);
    }

    /**
     * DELETE /emd/backoffice/api/v1/message-core/{entityId}/{messageId} — message not found → 404 (o 4xx generico).
     */
    @Test
    void deleteMessage_NotFound_Returns4xxError() {
        String entityId = "ENT-123";
        String messageId = "MSG-999";

        when(messageService.deleteMessage(entityId, messageId))
                .thenReturn(Mono.error(new ResourceNotFoundException("MESSAGE", "id " + messageId + " for entity " + entityId)));

        webTestClient.delete()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .exchange()
                .expectStatus().is4xxClientError();
    }

    /**
     * DELETE /emd/backoffice/api/v1/message-core/{entityId}/{messageId} — service error → 502 Bad Gateway (o 5xx).
     */
    @Test
    void deleteMessage_ServiceError_Returns5xx() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        when(messageService.deleteMessage(entityId, messageId))
                .thenReturn(Mono.error(new ExternalServiceException("MESSAGE_SERVICE", "deleteMessage", "Upstream error")));

        webTestClient.delete()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
