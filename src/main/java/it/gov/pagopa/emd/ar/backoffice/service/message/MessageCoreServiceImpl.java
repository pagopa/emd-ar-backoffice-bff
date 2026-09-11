package it.gov.pagopa.emd.ar.backoffice.service.message;

import org.springframework.stereotype.Service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class MessageCoreServiceImpl implements MessageCoreService {
    
    private final MessageCoreConnector messageConnector;

    private final TppConnector tppConnector;

    public MessageCoreServiceImpl(MessageCoreConnector messageConnector, TppConnector tppConnector) {
        this.messageConnector = messageConnector;
        this.tppConnector = tppConnector;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<MessageDTOV1> getMessageByMessageId(String messageId) {
        log.info("[AR-BFF][MESSAGE_GET] Getting Message by messageId={}", messageId);
        
        return messageConnector.getMessageByMessageId(messageId)
            .flatMap(message -> {
                if (message.getEntityId() == null || message.getEntityId().isBlank()) {
                    return Mono.just(message);
                }

                // Retrieve TPP info by entityId to enrich the message with businessName
                return tppConnector.getTppByEntityId(message.getEntityId())
                    .map(tppResponse -> {
                        message.setBusinessName(tppResponse.getBusinessName());
                        return message;
                    })
                    .onErrorResume(error -> {
                        log.warn("[AR-BFF][MESSAGE_GET] Failed to retrieve TPP info for entityId={}: {}.",
                                message.getEntityId(), error.getMessage());
                        return Mono.just(message);
                    });
            })
            .doOnSuccess(r -> log.info("[AR-BFF][MESSAGE_GET] Successfully processed messageId={}", messageId))
            .doOnError(e -> log.error("[AR-BFF][MESSAGE_GET] Error processing messageId={}: {}", messageId, e.getMessage()));
    }
}
