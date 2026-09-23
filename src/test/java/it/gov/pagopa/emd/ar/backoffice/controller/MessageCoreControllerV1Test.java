package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller.MessageCoreControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.handler.ControllerExceptionHandler;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.LogsDTO;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.LogsResponseDTO;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.azure.AzureServiceImpl;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unit tests for {@link MessageCoreControllerImplV1}.
 */
public class MessageCoreControllerV1Test {

    private MessageCoreService messageService;
    private AzureServiceImpl azureService;
    private WebTestClient webTestClient;
    private Utilities utilities;

    @BeforeEach
    void setUp() {
        messageService = Mockito.mock(MessageCoreService.class);
        azureService = Mockito.mock(AzureServiceImpl.class);
        MessageCoreControllerImplV1 messageController = new MessageCoreControllerImplV1(messageService, azureService);

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
                .page(0)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .build();

        // Mocking del servizio
        // Usiamo any() per LocalDateTime e List per evitare problemi di uguaglianza esatta tra oggetti
        when(messageService.searchMessages(
                eq("MSG-123"),
                eq("REC-456"),
                eq("ORG-789"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0),
                eq(10),
                anyList()))
            .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/emd/backoffice/api/v1/message-core/search")
                        .queryParam("messageId", "MSG-123")
                        .queryParam("recipientId", "REC-456")
                        .queryParam("originId", "ORG-789")
                        .queryParam("startDate", "2026-10-01T10:00:00")
                        .queryParam("endDate", "2026-10-31T10:00:00")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("fields", "id", "status")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalElements").isEqualTo(0);
        
        Mockito.verify(messageService).searchMessages(any(), any(), any(), any(), any(), anyInt(), anyInt(), any());
    }

    /**
     * GET /emd/backoffice/api/v1/message-core/search — no filters → 200 con parametri default.
     */
    @Test
    void searchMessages_NoFilters_Returns200WithDefaults() {
        MessageSearchResponseDTOV1 response = MessageSearchResponseDTOV1.builder()
                .page(0)
                .size(10)
                .build();

        when(messageService.searchMessages(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10), isNull()))
                .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/search")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(10);
    }

    /**
     * GET /emd/backoffice/api/v1/message-core/search — service error → 502 Bad Gateway.
     */
    @Test
    void searchMessages_ServiceError_Returns502() {
        when(messageService.searchMessages(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(Mono.error(new ExternalServiceException("MESSAGE_SERVICE", "searchMessages", "Upstream error")));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/search")
                .exchange()
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

    // ── getAzureLogs ────────────────────────────────────────────────────────

    /**
     * GET /emd/backoffice/api/v1/message-core/logs/{entityId}/{messageId} 
     * Happy path con parametri di paginazione custom → 200 OK con i log.
     */
    @Test
    void getAzureLogs_WithPaginationParams_Returns200WithLogs() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";
        int page = 1;
        int size = 5;

        LogsDTO logEntry = LogsDTO.builder()
                .timestamp("2026-10-01T10:00:00Z")
                .message("Test log message")
                .level("INFO")
                .traceId("trace-123")
                .build();

        LogsResponseDTO response = LogsResponseDTO.builder()
                .content(List.of(logEntry))
                .page(page)
                .size(size)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(azureService.fetchLogsFromAzure(entityId, messageId, page, size))
                .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/emd/backoffice/api/v1/message-core/logs/{entityId}/{messageId}")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(entityId, messageId))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(5)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].message").isEqualTo("Test log message")
                .jsonPath("$.content[0].level").isEqualTo("INFO");

        verify(azureService, times(1)).fetchLogsFromAzure(entityId, messageId, page, size);
    }

    /**
     * GET /emd/backoffice/api/v1/message-core/logs/{entityId}/{messageId}
     * Happy path senza parametri di paginazione → 200 OK con i default (page=0, size=10).
     */
    @Test
    void getAzureLogs_NoPaginationParams_Returns200WithDefaults() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        LogsResponseDTO response = LogsResponseDTO.builder()
                .content(List.of()) // Nessun log trovato, ma la chiamata ha successo
                .page(0)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .build();

        // Ci aspettiamo che il controller chiami il service con i default 0 e 10
        when(azureService.fetchLogsFromAzure(entityId, messageId, 0, 10))
                .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/logs/{entityId}/{messageId}", entityId, messageId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(10)
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content").isEmpty();

        verify(azureService, times(1)).fetchLogsFromAzure(entityId, messageId, 0, 10);
    }

    /**
     * GET /emd/backoffice/api/v1/message-core/logs/{entityId}/{messageId}
     * Errore dal servizio Azure → 502 Bad Gateway (o 500 a seconda dell'ExceptionHandler).
     */
    @Test
    void getAzureLogs_ServiceError_Returns5xx() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        when(azureService.fetchLogsFromAzure(entityId, messageId, 0, 10))
                .thenReturn(Mono.error(new ExternalServiceException("AZURE_SERVICE", "fetchLogsFromAzure", "Timeout contacting Azure Monitor")));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/message-core/logs/{entityId}/{messageId}", entityId, messageId)
                .exchange()
                .expectStatus().is5xxServerError();

        verify(azureService, times(1)).fetchLogsFromAzure(entityId, messageId, 0, 10);
    }
}
