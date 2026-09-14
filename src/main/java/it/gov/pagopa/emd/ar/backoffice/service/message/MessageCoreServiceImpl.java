package it.gov.pagopa.emd.ar.backoffice.service.message;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
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
    
}
