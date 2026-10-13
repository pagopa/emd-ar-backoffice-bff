package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class MessageCoreControllerImplV1 implements MessageCoreControllerV1 {
    
    private final MessageCoreService messageService;

    public MessageCoreControllerImplV1(MessageCoreService messageService) {
        this.messageService = messageService;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<MessageSearchResponseDTOV1>> searchMessages(
        String messageId, String recipientId, String originId, LocalDateTime startDate, LocalDateTime endDate,
        String cursor, int size, List<String> fields) {
        log.info("[AR-BFF][MESSAGE_SEARCH] Request received - Searching messages — messageId={}, recipientId={}, originId={}, startDate={}, endDate={}, cursor={}, size={}, fields={}",
                messageId != null ? "***" : null, recipientId != null ? "***" : null, originId != null ? "***" : null,
                startDate, endDate, cursor, size, fields != null ? fields.size() : 0);
        return messageService.searchMessages(messageId, recipientId, originId, startDate, endDate, cursor, size, fields)
                .map(ResponseEntity::ok);
    }


    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<MessageDTOV1>> getMessageByEntityIdAndMessageId(String entityId, String messageId) {
        log.info("[AR-BFF][MESSAGE_GET] Request received - Getting Message by entityId={} and messageId={}", entityId, messageId);
        return messageService.getMessageByEntityIdAndMessageId(entityId, messageId)
                .map(ResponseEntity::ok);
    }
    
    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<Void>> deleteMessage(String entityId, String messageId, String authHeader){
        String userEmail = Utilities.getEmailFromToken(authHeader);

        log.info("[AR-BFF][MESSAGE_DELETE][User:{}] Request received - Deleting Message by entityId={} and messageId={}", userEmail, entityId, messageId);
        return messageService.deleteMessage(entityId, messageId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
