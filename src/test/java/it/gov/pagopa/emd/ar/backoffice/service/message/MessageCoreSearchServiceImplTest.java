package it.gov.pagopa.emd.ar.backoffice.service.message;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchParameterV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException;

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
import static org.mockito.Mockito.*;

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
        // Given
        String nextCursor = "next-cursor";
        int size = 10;
        
        MessageSearchParameterV1 params = new MessageSearchParameterV1();
        params.setMessageId("MSG-123");
        params.setRecipientId("REC-456");
        params.setOriginId("ORG-789");
        params.setCursor(nextCursor);
        params.setSize(size);

        MessageSearchResponseDTOV1 connectorResponse = pageOf(nextCursor, size, 1);
        
        when(messageConnector.searchMessages(any(MessageSearchParameterV1.class)))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchMessages(params))
                .assertNext(dto -> {
                    assertThat(dto.getNextCursor()).isEqualTo(nextCursor);
                    assertThat(dto.getTotalElements()).isEqualTo(1L);
                    assertThat(dto.getSize()).isEqualTo(size);
                })
                .verifyComplete();

        verify(messageConnector).searchMessages(eq(params));
    }

    /**
     * Ricerca per range date — verifica che le date vengano passate senza alterazioni.
     */
    @Test
    void searchMessages_ByDateRange_PassesDatesToConnector() {
        // Given
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        
        MessageSearchParameterV1 params = new MessageSearchParameterV1();
        params.setStartDate(start);
        params.setEndDate(end);
        params.setSize(10);

        MessageSearchResponseDTOV1 connectorResponse = pageOf(null, 10, 5);

        when(messageConnector.searchMessages(eq(params)))
                .thenReturn(Mono.just(connectorResponse));

        // When & Then
        StepVerifier.create(service.searchMessages(params))
                .assertNext(dto -> assertThat(dto.getTotalElements()).isEqualTo(5L))
                .verifyComplete();
        
        // Verifica dell'invocazione
        verify(messageConnector).searchMessages(eq(params));
    }

    /**
     * Risposta vuota — il service propaga correttamente una pagina senza risultati.
     */
    @Test
    void searchMessages_EmptyResult_ReturnsEmptyPage() {
        // Given
        MessageSearchParameterV1 params = new MessageSearchParameterV1();
        params.setMessageId("NOT-FOUND");
        params.setSize(10);

        MessageSearchResponseDTOV1 emptyResponse = pageOf(null, 10, 0);

        // Mock del connector
        when(messageConnector.searchMessages(any(MessageSearchParameterV1.class)))
                .thenReturn(Mono.just(emptyResponse));

        // When & Then
        StepVerifier.create(service.searchMessages(params))
                .assertNext(dto -> {
                    assertThat(dto.getContent()).isEmpty();
                    assertThat(dto.getTotalElements()).isZero();
                })
                .verifyComplete();

        // Verifica che l'oggetto corretto sia arrivato al connector
        verify(messageConnector).searchMessages(eq(params));
    }

    /**
     * Errore upstream — {@link ExternalServiceException} viene propagata dal connector attraverso il service.
     */
    @Test
    void searchMessages_UpstreamError_PropagatesException() {
        // Given
        MessageSearchParameterV1 params = new MessageSearchParameterV1();
        params.setSize(10);

        when(messageConnector.searchMessages(any(MessageSearchParameterV1.class)))
                .thenReturn(Mono.error(new ExternalServiceException(
                        "MESSAGE_SERVICE", "searchMessages", "Connection failed")));

        // When & Then
        StepVerifier.create(service.searchMessages(params))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                                    ex.getMessage().contains("MESSAGE_SERVICE"))
                .verify();
    }

    /**
     * Proiezione campi — verifica che la lista {@code fields} sia inoltrata al connector.
     */
    @Test
    void searchMessages_WithFields_PassesFieldsToConnector() {
        // Given
        List<String> fields = List.of("id", "status", "timestamp");
        MessageSearchParameterV1 params = new MessageSearchParameterV1();
        params.setFields(fields);
        params.setSize(10);

        MessageSearchResponseDTOV1 connectorResponse = pageOf(null, 10, 1);
        
        when(messageConnector.searchMessages(eq(params)))
                .thenReturn(Mono.just(connectorResponse));

        // When & Then
        StepVerifier.create(service.searchMessages(params))
                .expectNextCount(1)
                .verifyComplete();

        // Verifica finale: l'oggetto passato al connector deve contenere la lista campi corretta
        verify(messageConnector).searchMessages(eq(params));
    }

    /**
     * Campo non valido — {@link InvalidSearchFieldException} viene propagata correttamente.
     */
    @Test
    void searchMessages_InvalidField_PropagatesException() {
        // Given
        List<String> invalidFields = List.of("unknownField");
        MessageSearchParameterV1 params = new MessageSearchParameterV1();
        params.setFields(invalidFields);
        params.setSize(10);
        
        when(messageConnector.searchMessages(eq(params)))
                .thenReturn(Mono.error(new InvalidSearchFieldException(
                        "unknownField", "Invalid field name")));

        // When & Then
        StepVerifier.create(service.searchMessages(params))
                .expectError(InvalidSearchFieldException.class)
                .verify();
    }
}