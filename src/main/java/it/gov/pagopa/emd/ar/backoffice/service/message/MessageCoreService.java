package it.gov.pagopa.emd.ar.backoffice.service.message;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import reactor.core.publisher.Mono;

public interface MessageCoreService {
    
    /**
     * Looks up an existing Message by its {@code entityId} and {@code messageId}.
     *
     * @param entityId the entity ID
     * @param messageId the message ID
     * @return {@code Mono<MessageResponseDTOV1>} with the Message details if found,
     *         or a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         (HTTP 404) if no Message exists for that messageId
     */
    Mono<MessageDTOV1> getMessageByEntityIdAndMessageId(String entityId, String messageId);
}
