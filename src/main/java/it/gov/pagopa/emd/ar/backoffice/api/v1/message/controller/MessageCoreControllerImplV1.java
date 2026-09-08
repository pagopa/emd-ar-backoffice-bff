package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController 
@Slf4j 
public class MessageCoreControllerImplV1 implements MessageCoreControllerV1 {
    
    private final MessageService messageService;

    public MessageCoreControllerImplV1(MessageService messageService) {
        this.messageService = messageService;
    }
   
    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<MessageSearchResponseDTOV1>> searchMessages(
        String messageId, String recipientId, String originId, 
        LocalDateTime startDate, LocalDateTime endDate,
        int page, int size, List<String> fields) {
        log.info("[AR-BFF][MESSAGE_SEARCH] Searching messages — messageId={}, recipientId={}, originId={}, startDate={}, endDate={}, page={}, size={}, fields={}",
                messageId != null ? "***" : null,
                recipientId != null ? "***" : null,
                originId != null ? "***" : null,
                startDate, endDate, page, size, fields != null ? fields.size() : 0);
        return messageService.searchMessages(messageId, recipientId, originId, startDate, endDate, page, size, fields)
                .map(ResponseEntity::ok);
    }

}
