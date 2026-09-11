package it.gov.pagopa.emd.ar.backoffice.connector.message;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import reactor.core.publisher.Mono;

public interface MessageCoreConnector {
    
    /**
     * Retrieves a Message by its {@code messageId}.
     *
     * @param messageId the message ID
     * @return {@code Mono<MessageDTOV1>} with the Message details if found,
     *         or a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         (HTTP 404) if no Message exists for that messageId
     */
    Mono<MessageDTOV1> getMessageByMessageId(String messageId);
}
