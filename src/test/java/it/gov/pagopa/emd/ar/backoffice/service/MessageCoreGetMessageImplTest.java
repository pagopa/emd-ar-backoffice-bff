package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class MessageCoreGetMessageImplTest {
    
    private MessageCoreConnector messageConnector;
    private TppConnector tppConnector;
    private MessageCoreServiceImpl messageService;

    @BeforeEach
    void setUp() {
        messageConnector = Mockito.mock(MessageCoreConnector.class);
        tppConnector = Mockito.mock(TppConnector.class);
        
        messageService = new MessageCoreServiceImpl(messageConnector, tppConnector);
    }

    // ── getMessageByMessageId ─────────────────────────────────────────────────

    /**
     * Il messaggio non ha un entityId (o è vuoto). Il TPP Connector non deve essere chiamato.
     */
    @Test
    void getMessageByMessageId_NoEntityId_ReturnsMessageAndSkipsTppCall() {
        String messageId = "msg-123";
        MessageDTOV1 expectedDto = new MessageDTOV1();
        expectedDto.setMessageId(messageId);
        expectedDto.setEntityId("   "); // Simuliamo una stringa blank
        
        when(messageConnector.getMessageByMessageId(messageId))
                .thenReturn(Mono.just(expectedDto));

        StepVerifier.create(messageService.getMessageByMessageId(messageId))
                .expectNext(expectedDto)
                .verifyComplete();

        verify(messageConnector, times(1)).getMessageByMessageId(messageId);
        verify(tppConnector, never()).getTppByEntityId(anyString()); // Verifica che non venga chiamato
    }

    /**
     * Il messaggio ha un entityId. Il TPP Connector viene chiamato e arricchisce il businessName.
     */
    @Test
    void getMessageByMessageId_WithEntityId_EnrichesBusinessName() {
        String messageId = "msg-123";
        String entityId = "entity-456";
        String tppBusinessName = "TPP S.p.A.";

        MessageDTOV1 messageDto = new MessageDTOV1();
        messageDto.setMessageId(messageId);
        messageDto.setEntityId(entityId);

        TppEntityIdResponse tppResponse = new TppEntityIdResponse();
        tppResponse.setEntityId(entityId);
        tppResponse.setBusinessName(tppBusinessName);
        
        when(messageConnector.getMessageByMessageId(messageId))
                .thenReturn(Mono.just(messageDto));
        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(tppResponse));

        StepVerifier.create(messageService.getMessageByMessageId(messageId))
                .assertNext(result -> {
                    assertEquals(messageId, result.getMessageId());
                    assertEquals(tppBusinessName, result.getBusinessName()); // Verifica arricchimento
                })
                .verifyComplete();

        verify(messageConnector, times(1)).getMessageByMessageId(messageId);
        verify(tppConnector, times(1)).getTppByEntityId(entityId);
    }

    /**
     * Il messaggio ha un entityId, ma la chiamata al TPP fallisce.
     * La funzione non si blocca e restituisce il messaggio originale senza arricchimento (businessName resta null).
     */
    @Test
    void getMessageByMessageId_TppCallFails_ReturnsOriginalMessage() {
        String messageId = "msg-123";
        String entityId = "entity-456";
        
        MessageDTOV1 messageDto = new MessageDTOV1();
        messageDto.setMessageId(messageId);
        messageDto.setEntityId(entityId);
        
        when(messageConnector.getMessageByMessageId(messageId))
                .thenReturn(Mono.just(messageDto));
                
        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));
                
        StepVerifier.create(messageService.getMessageByMessageId(messageId))
                .assertNext(result -> {
                    assertEquals(messageId, result.getMessageId());
                    assertNull(result.getBusinessName()); // Verifica che sia rimasto null
                })
                .verifyComplete();
                
        verify(messageConnector, times(1)).getMessageByMessageId(messageId);
        verify(tppConnector, times(1)).getTppByEntityId(entityId);
    }

    /**
     * getMessageByMessageId — message non trovato → propaga la ResourceNotFoundException emessa dal connector message.
     */
    @Test
    void getMessageByMessageId_NotFound_PropagatesException() {
        String messageId = "msg-404";
        ResourceNotFoundException notFoundException = new ResourceNotFoundException("MESSAGE", messageId);
        
        when(messageConnector.getMessageByMessageId(messageId))
                .thenReturn(Mono.error(notFoundException));

        StepVerifier.create(messageService.getMessageByMessageId(messageId))
                .expectErrorMatches(throwable -> 
                        throwable instanceof ResourceNotFoundException &&
                        throwable.getMessage().contains(messageId))
                .verify();

        verify(messageConnector, times(1)).getMessageByMessageId(messageId);
        verify(tppConnector, never()).getTppByEntityId(anyString()); // TPP non deve essere chiamato
    }
}