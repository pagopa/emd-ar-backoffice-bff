package it.gov.pagopa.emd.ar.backoffice.connector.message;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchParameterV1;
import reactor.core.publisher.Mono;

public interface MessageCoreConnector {
    
    /**
     * Performs a paginated search for Messages by calling
     * {@code GET /emd/message-core/search} on the emd-message service.
     *
     * @param request      the search parameters object.
     * @return {@code Mono<MessageSearchResponseDTOV1>} with the paginated result.
     */
    Mono<MessageSearchResponseDTOV1> searchMessages(MessageSearchParameterV1 request);

    /**
     * Retrieves a Message by its {@code entityId} ands {@code messageId}.
     *
     * @param entityId the entity ID
     * @param messageId the message ID
     * @return {@code Mono<MessageDTOV1>} with the Message details if found,
     *         or a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         (HTTP 404) if no Message exists for that messageId
     */
    Mono<MessageDTOV1> getMessageByEntityIdAndMessageId(String entityId, String messageId);

    /**
     * Deletes a message on the remote emd-message-core service.
     * 
     * @param entityId  the identifier of the tpp
     * @param messageId the identifier of the message
     * @return an empty {@code Mono<Void>} upon successful deletion
     */
    Mono<Void> deleteMessageByEntityIdAndMessageId(String entityId, String messageId);
}
