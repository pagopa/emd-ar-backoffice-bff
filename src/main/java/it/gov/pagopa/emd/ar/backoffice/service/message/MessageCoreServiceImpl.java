package it.gov.pagopa.emd.ar.backoffice.service.message;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.message.MessageCoreConnector;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;

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
    public Mono<MessageSearchResponseDTOV1> searchMessages(String messageId, String recipientId, String originId, LocalDateTime startDate, LocalDateTime endDate, int page, int size, List<String> fields) {
        log.info("[AR-BFF][MESSAGE_SEARCH] Searching messages — messageId={}, recipientId={}, originId={}, startDate={}, endDate={}, page={}, size={}, fields={}",
                messageId != null ? "***" : null,
                recipientId != null ? "***" : null,
                originId != null ? "***" : null,
                startDate, endDate, page, size, fields != null ? fields.size() : 0);
        return messageConnector.searchMessages(messageId, recipientId, originId, startDate, endDate, page, size, fields)
            .doOnSuccess(r -> log.info("[AR-BFF][MESSAGE_SEARCH] Search completed: totalElements={}, totalPages={}",
                    r.getTotalElements(), r.getTotalPages()))
            .doOnError(e -> log.error("[AR-BFF][MESSAGE_SEARCH] Search failed: {}", e.getMessage()));
    }
    
    /** {@inheritDoc} */
    @Override
    public Mono<MessageDTOV1> getMessageByEntityIdAndMessageId(String entityId, String messageId) {
        log.info("[AR-BFF][MESSAGE_GET] Getting Message by entityId={} and messageId={}", entityId, messageId);
        
        return messageConnector.getMessageByEntityIdAndMessageId(entityId, messageId)
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

    /** {@inheritDoc} */
    @Override
    public Mono<Void> deleteMessage(String entityId, String messageId) {
        log.info("[AR-BFF][MESSAGE_DELETE] Deleting message through connector - entityId={}, messageId={}", entityId, messageId);
        
        return messageConnector.deleteMessageByEntityIdAndMessageId(entityId, messageId)
                .doOnSuccess(v -> log.info("[AR-BFF][MESSAGE_DELETE] Delete successful for messageId={}", messageId))
                .doOnError(e -> log.error("[AR-BFF][MESSAGE_DELETE] Delete failed for messageId={}: {}", messageId, e.getMessage()));
    }
}
