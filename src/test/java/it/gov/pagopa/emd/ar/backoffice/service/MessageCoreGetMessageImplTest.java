package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessageCoreGetMessageImplTest {
    
    private MessageCoreConnector messageConnector;
    private MessageCoreServiceImpl messageService;

    @BeforeEach
    void setUp() {
        messageConnector = Mockito.mock(MessageCoreConnector.class);
        
        messageService = new MessageCoreServiceImpl(messageConnector);
    }

     // ── getMessageByMessageId ─────────────────────────────────────────────────

    /**
     * getMessageByMessageId — happy path → restituisce un Mono contenente il MessageDTOV1.
     */
    @Test
    void getMessageByMessageId_Success_ReturnsMessageDTO() {
        // Arrange
        String messageId = "msg-123";
        MessageDTOV1 expectedDto = new MessageDTOV1();
        
        when(messageConnector.getMessageByMessageId(messageId))
                .thenReturn(Mono.just(expectedDto));

        // Act & Assert
        StepVerifier.create(messageService.getMessageByMessageId(messageId))
                .expectNext(expectedDto)
                .verifyComplete();

        verify(messageConnector, times(1)).getMessageByMessageId(messageId);
    }

    /**
     * getMessageByMessageId — message non trovato → propaga la ResourceNotFoundException emessa dal connector.
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
    }

}
