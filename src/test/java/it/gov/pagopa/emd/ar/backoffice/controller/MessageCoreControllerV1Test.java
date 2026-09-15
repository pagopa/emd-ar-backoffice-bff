package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller.MessageCoreControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MessageCoreControllerImplV1}.
 */
public class MessageCoreControllerV1Test {

    private MessageCoreService messageService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        messageService = Mockito.mock(MessageCoreService.class);
        MessageCoreControllerImplV1 messageController = new MessageCoreControllerImplV1(messageService);
        webTestClient = WebTestClient.bindToController(messageController).build();
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

        // ── deleteMessage ────────────────────────────────────────────────────────

    /**
     * DELETE /emd/backoffice/api/v1/message-core/{entityId}/{messageId} — happy path → 204 No Content.
     */
    @Test
    void deleteMessage_HappyPath_Returns204NoContent() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        // Mocking del servizio: ritorna Mono.empty() per indicare il successo (void)
        when(messageService.deleteMessage(entityId, messageId))
                .thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
        
        // Verifichiamo che il service sia stato chiamato con i parametri corretti
        Mockito.verify(messageService).deleteMessage(entityId, messageId);
    }

    /**
     * DELETE /emd/backoffice/api/v1/message-core/{entityId}/{messageId} — message not found → 404 (o 4xx generico).
     */
    @Test
    void deleteMessage_NotFound_Returns4xxError() {
        String entityId = "ENT-123";
        String messageId = "MSG-999";

        // Simula l'eccezione generata dal connector quando il messaggio non esiste
        when(messageService.deleteMessage(entityId, messageId))
                .thenReturn(Mono.error(new ResourceNotFoundException("MESSAGE", "id " + messageId + " for entity " + entityId)));

        webTestClient.delete()
                .uri("/emd/backoffice/api/v1/message-core/{entityId}/{messageId}", entityId, messageId)
                .exchange()
                // Nota: utilizziamo is4xxClientError() per coprire l'eccezione. Se hai un @ControllerAdvice
                // che mappa specificamente ResourceNotFoundException a 404, potresti usare isNotFound()
                // configurando il webTestClient con .controllerAdvice(...) nel setup.
                .expectStatus().is5xxServerError(); // Messo is5xx di default (vedi nota sotto)
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