package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
    public Mono<ResponseEntity<MessageDTOV1>> getMessageByMessageId(String messageId) {
        log.info("[AR-BFF][MESSAGE_GET] Getting Message by messageId={}", messageId);
        return messageService.getMessageByMessageId(messageId)
                .map(ResponseEntity::ok);
    }
    
}
