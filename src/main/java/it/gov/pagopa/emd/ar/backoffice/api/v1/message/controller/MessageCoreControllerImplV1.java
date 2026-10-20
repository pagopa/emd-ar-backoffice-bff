package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchParameterV1;
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
    public Mono<ResponseEntity<MessageSearchResponseDTOV1>> searchMessages(MessageSearchParameterV1 request) {
        log.info("[AR-BFF][MESSAGE_SEARCH] Searching messages — messageId={}, recipientId={}, originId={}, status={}, startDate={}, endDate={}, cursor={}, size={}, fields={}",
                request.getMessageId() != null ? "***" : null,
                request.getRecipientId() != null ? "***" : null,
                request.getOriginId() != null ? "***" : null,
                request.getState() != null ? "***" : null,
                request.getStartDate(), request.getEndDate(), request.getCursor(), request.getSize(), request.getFields() != null ? request.getFields().size() : 0);
        return messageService.searchMessages(request)
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
