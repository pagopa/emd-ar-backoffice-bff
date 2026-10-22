package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchParameterV1;
import reactor.core.publisher.Mono;

@RequestMapping ("/emd/backoffice/api/v1")
public interface MessageCoreControllerV1 {
    
    /**
     * Performs a paginated search for Messages.
     *
     * <p>At least one filter ({@code messageId} or {@code recipientId} or {@code originId}) or messages sent
     * between {@code startDate} and {@code endDate} should be provided
     * for meaningful results, though both are technically optional.</p>
     *
     * @param request      the search parameters including filters (messageId, recipientId, originId, state, dates),
     *                     pagination (cursor, size) and projection fields.
     * @return {@code Mono<ResponseEntity<MessageSearchResponseDTOV1>>} HTTP 200 with the
     *         paginated result, HTTP 400 for an invalid field name, or HTTP 502 if
     *         the upstream emd-message-core service is unavailable
     */
    @GetMapping(value = "message-core/search", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<MessageSearchResponseDTOV1>> searchMessages(
        @ModelAttribute MessageSearchParameterV1 request);

    /**
     * Checks whether a Message with the given {@code entityId} and {@code messageId} already exists
     * and returns its details.
     *
     * <p>Returns HTTP 200 with a {@code MessageDTOV1} payload if found, or HTTP 404 if no Message
     * exists for those ids.</p>
     *
     * @param entityId the ID of the tpp
     * @param messageId the ID of the message to retrieve
     * @return {@code Mono<ResponseEntity<MessageDTOV1>>} with message details, or 404
     */
    @GetMapping(value = "message-core/{entityId}/{messageId}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<MessageDTOV1>> getMessageByEntityIdAndMessageId(
            @PathVariable("entityId") String entityId,
            @PathVariable("messageId") String messageId);

    /**
     * Delete a Message from Database by entityId and messageId.
     * 
     * @param entityId the identifier of the tpp
     * @param messageId the identifier of the message
     * @param authHeader  (Optional) the authorization token for the request
     * @return an empty {@link Mono} resulting in a 204 No Content response
     */
    @DeleteMapping(value = "message-core/{entityId}/{messageId}")
    Mono<ResponseEntity<Void>> deleteMessage(
            @PathVariable("entityId") String entityId,
            @PathVariable("messageId") String messageId,
            @RequestHeader(name = "Authorization", required = false) String authHeader);
            
}
