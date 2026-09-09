package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller.MessageCoreControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageService;
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
public class MessageControllerV1Test {

    private MessageService messageService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        messageService = Mockito.mock(MessageService.class);
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
}