package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests per il metodo {@code searchMessages} di {@link MessageCoreServiceImpl}.
 *
 * <p>Il {@link MessageCoreConnector} viene mockato per isolare la logica del servizio.
 * In questa implementazione, il servizio agisce principalmente come delegato verso il connector,
 * gestendo il logging e la propagazione delle risposte reattive.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Ricerca per filtri ID (messageId, recipientId, originId) — delega corretta</li>
 *   <li>Ricerca per range temporale (startDate, endDate) — passaggio corretto delle date</li>
 *   <li>Risposta con lista vuota</li>
 *   <li>Errore upstream (502) → {@link ExternalServiceException} propagata</li>
 *   <li>Errore di validazione campi (400) → {@link InvalidSearchFieldException} propagata</li>
 *   <li>Paginazione e proiezione campi (fields) — parametri inoltrati correttamente</li>
 * </ol>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MessageCoreSearchServiceImplTest {

    @Mock
    private MessageCoreConnector messageConnector;

    @Mock
    private TppConnector tppConnector;

    private MessageCoreServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageCoreServiceImpl(messageConnector, tppConnector);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MessageSearchResponseDTOV1 pageOf(String cursor, int size, long total) {
        return MessageSearchResponseDTOV1.builder()
                .content(List.of())
                .nextCursor(cursor)
                .size(size)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Ricerca per filtri ID — il service delega correttamente i parametri al connector.
     */
    @Test
    void searchMessages_ByFilters_ReturnsResponse() {
        MessageSearchResponseDTOV1 connectorResponse = pageOf("next-cursor", 10, 1);
        
        when(messageConnector.searchMessages(eq("MSG-123"), eq("REC-456"), eq("ORG-789"), 
                isNull(), isNull(), eq("next-cursor"), eq(10), isNull()))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchMessages("MSG-123", "REC-456", "ORG-789", null, null, "next-cursor", 10, null))
                .assertNext(dto -> {
                    assertThat(dto.getNextCursor()).isEqualTo("next-cursor");
                    assertThat(dto.getTotalElements()).isEqualTo(1);
                })
                .verifyComplete();
    }

    /**
     * Ricerca per range date — verifica che le date vengano passate senza alterazioni.
     */
    @Test
    void searchMessages_ByDateRange_PassesDatesToConnector() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        MessageSearchResponseDTOV1 connectorResponse = pageOf(null, 10, 5);

        when(messageConnector.searchMessages(isNull(), isNull(), isNull(),
                eq(start), eq(end), isNull(), eq(10), isNull()))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchMessages(null, null, null, start, end, null, 10, null))
                .assertNext(dto -> assertThat(dto.getTotalElements()).isEqualTo(5))
                .verifyComplete();
    }

    /**
     * Risposta vuota — il service propaga correttamente una pagina senza risultati.
     */
    @Test
    void searchMessages_EmptyResult_ReturnsEmptyPage() {
        MessageSearchResponseDTOV1 emptyResponse = pageOf(null, 10, 0);

        when(messageConnector.searchMessages(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(Mono.just(emptyResponse));

        StepVerifier.create(service.searchMessages("NOT-FOUND", null, null, null, null, null, 10, null))
                .assertNext(dto -> {
                    assertThat(dto.getContent()).isEmpty();
                    assertThat(dto.getTotalElements()).isZero();
                })
                .verifyComplete();
    }

    /**
     * Errore upstream — {@link ExternalServiceException} viene propagata dal connector attraverso il service.
     */
    @Test
    void searchMessages_UpstreamError_PropagatesException() {
        when(messageConnector.searchMessages(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(Mono.error(new ExternalServiceException("MESSAGE_SERVICE", "searchMessages", "Connection failed")));

        StepVerifier.create(service.searchMessages(null, null, null, null, null, null, 10, null))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                                    ex.getMessage().contains("MESSAGE_SERVICE"))
                .verify();
    }

    /**
     * Proiezione campi — verifica che la lista {@code fields} sia inoltrata al connector.
     */
    @Test
    void searchMessages_WithFields_PassesFieldsToConnector() {
        List<String> fields = List.of("id", "status", "timestamp");
        MessageSearchResponseDTOV1 connectorResponse = pageOf(null, 10, 1);

        when(messageConnector.searchMessages(any(), any(), any(), any(), any(), any(), anyInt(), eq(fields)))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchMessages(null, null, null, null, null, null, 10, fields))
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * Campo non valido — {@link InvalidSearchFieldException} viene propagata correttamente.
     */
    @Test
    void searchMessages_InvalidField_PropagatesException() {
        List<String> invalidFields = List.of("unknownField");
        
        when(messageConnector.searchMessages(any(), any(), any(), any(), any(), any(), anyInt(), eq(invalidFields)))
                .thenReturn(Mono.error(new InvalidSearchFieldException("unknownField", "Invalid field name")));

        StepVerifier.create(service.searchMessages(null, null, null, null, null, null, 10, invalidFields))
                .expectError(InvalidSearchFieldException.class)
                .verify();
    }
}