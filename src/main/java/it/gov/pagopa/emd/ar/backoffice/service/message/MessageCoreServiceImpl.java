package it.gov.pagopa.emd.ar.backoffice.service.message;

import org.springframework.stereotype.Service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class MessageCoreServiceImpl implements MessageCoreService {
    
    private final MessageCoreConnector messageConnector;

    public MessageCoreServiceImpl(MessageCoreConnector messageConnector) {
        this.messageConnector = messageConnector;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<MessageDTOV1> getMessageByMessageId(String messageId) {
        log.info("[AR-BFF][MESSAGE_GET] Getting Message by messageId={}", messageId);
        return messageConnector.getMessageByMessageId(messageId)
        .doOnSuccess(r -> log.info("[AR-BFF][MESSAGE_GET] Found Message for messageId={}", messageId))
        .doOnError(e -> log.warn("[AR-BFF][MESSAGE_GET] Message not found for messageId={}: {}", messageId, e.getMessage()));
    }
}
