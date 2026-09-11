package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import reactor.core.publisher.Mono;

@RequestMapping ("/emd/backoffice/api/v1")
public interface MessageCoreControllerV1 {
    
    /**
     * Checks whether a Message with the given {@code messageId} already exists
     * and returns its details.
     *
     * <p>Returns HTTP 200 with a {@code MessageDTOV1} payload if found, or HTTP 404 if no Message
     * exists for that {@code messageId}.</p>
     *
     * @param messageId the ID of the message to retrieve
     * @return {@code Mono<ResponseEntity<MessageDTOV1>>} with message details, or 404
     */
    @GetMapping(value = "message-core/{messageId}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<MessageDTOV1>> getMessageByMessageId(
            @PathVariable("messageId") String messageId);

}