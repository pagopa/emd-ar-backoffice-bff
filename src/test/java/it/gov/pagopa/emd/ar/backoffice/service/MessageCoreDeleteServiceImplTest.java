package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

/**
 * Unit tests per il metodo {@code deleteMessage} di {@link MessageCoreServiceImpl}.
 *
 * <p>Il {@link MessageCoreConnector} viene mockato per isolare la logica del servizio.
 * In questa implementazione, il servizio agisce come pass-through verso il connector,
 * aggiungendo logica di logging per il tracciamento di successi ed errori.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Cancellazione con successo (Happy path) → Mono vuoto completato</li>
 *   <li>Messaggio non trovato (404 upstream) → {@link ResourceNotFoundException} propagata</li>
 *   <li>Errore generico upstream (5xx) → {@link ExternalServiceException} propagata</li>
 * </ol>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MessageCoreDeleteServiceImplTest {

    @Mock
    private MessageCoreConnector messageConnector;

    @Mock
    private TppConnector tppConnector;

    private MessageCoreServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageCoreServiceImpl(messageConnector, tppConnector);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Cancellazione avvenuta con successo. 
     * Il connector ritorna Mono.empty() e il service completa senza errori.
     */
    @Test
    void deleteMessage_HappyPath_CompletesSuccessfully() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        when(messageConnector.deleteMessageByEntityIdAndMessageId(entityId, messageId))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.deleteMessage(entityId, messageId))
                .verifyComplete();
    }

    /**
     * Il messaggio non esiste.
     * Il connector lancia una ResourceNotFoundException e il service la propaga.
     */
    @Test
    void deleteMessage_NotFound_PropagatesException() {
        String entityId = "ENT-123";
        String messageId = "MSG-999";

        when(messageConnector.deleteMessageByEntityIdAndMessageId(entityId, messageId))
                .thenReturn(Mono.error(new ResourceNotFoundException("MESSAGE", "id MSG-999 for entity ENT-123")));

        StepVerifier.create(service.deleteMessage(entityId, messageId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException &&
                        ex.getMessage().contains("MSG-999"))
                .verify();
    }

    /**
     * Errore di connessione o errore server (502/500).
     * Il connector lancia ExternalServiceException e il service la propaga.
     */
    @Test
    void deleteMessage_UpstreamError_PropagatesException() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        when(messageConnector.deleteMessageByEntityIdAndMessageId(entityId, messageId))
                .thenReturn(Mono.error(new ExternalServiceException("MESSAGE_SERVICE", "deleteMessageByEntityIdAndMessageId", "Bad Gateway")));

        StepVerifier.create(service.deleteMessage(entityId, messageId))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException &&
                        ex.getMessage().contains("MESSAGE_SERVICE"))
                .verify();
    }
}