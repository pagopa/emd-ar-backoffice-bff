package it.gov.pagopa.emd.ar.backoffice.service.message;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchParameterV1;
import reactor.core.publisher.Mono;

public interface MessageCoreService {
    
    /**
     * Searches for Messages using a paginated, filtered query.
     *
     * <p>Delegates directly to the upstream {@code GET /emd/message-core/searchV2} endpoint via the
     * connector. Filter parameters are optional.
     * </p>
     *
     * <p>The optional {@code fields} list restricts which fields are populated in each
     * element of {@code content}. An invalid field name causes HTTP 400.</p>
     *
     * @param request      the object containing all search and pagination filters.
     * @return {@code Mono<MessageSearchResponseDTOV1>} with the paginated result.
     */
    Mono<MessageSearchResponseDTOV1> searchMessages(MessageSearchParameterV1 request);

    /**
     * Looks up an existing Message by its {@code entityId} and {@code messageId}.
     *
     * @param entityId the entity ID
     * @param messageId the message ID
     * @return {@code Mono<MessageDTOV1>} with the Message details if found,
     *         or a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         (HTTP 404) if no Message exists for that messageId
     */
    Mono<MessageDTOV1> getMessageByEntityIdAndMessageId(String entityId, String messageId);

    /**
     * Deletes a Message by entityId and messageId
     * 
     * @param entityId the identifier of the tpp
     * @param messageId the identifier of the message
     * @return a {@link Mono<Void>} that completes when the deletion is successful
     */
    Mono<Void> deleteMessage(String entityId, String messageId);
}
