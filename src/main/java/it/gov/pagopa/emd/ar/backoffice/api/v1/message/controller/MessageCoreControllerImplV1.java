package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.LogsResponseDTO;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.azure.AzureService;
import it.gov.pagopa.emd.ar.backoffice.service.message.MessageCoreService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class MessageCoreControllerImplV1 implements MessageCoreControllerV1 {
    
    private final MessageCoreService messageService;

    private final AzureService azureService;


    public MessageCoreControllerImplV1(MessageCoreService messageService, AzureService azureService) {
        this.messageService = messageService;
        this.azureService = azureService;
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


    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<MessageDTOV1>> getMessageByEntityIdAndMessageId(String entityId, String messageId) {
        log.info("[AR-BFF][MESSAGE_GET] Getting Message by entityId={} and messageId={}", entityId, messageId);
        return messageService.getMessageByEntityIdAndMessageId(entityId, messageId)
                .map(ResponseEntity::ok);
    }
    
    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<Void>> deleteMessage(String entityId, String messageId){
        return messageService.deleteMessage(entityId, messageId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @Override
    public Mono<ResponseEntity<LogsResponseDTO>> getAzureLogs(String entityId, String messageId, int page, int size) {
        log.info("[AR-BFF][MESSAGE_LOGS] Fetching Azure Logs for entityId={} and messageId={} (page={}, size={})", 
                entityId, messageId, page, size);
        return azureService.fetchAllLogsFromAzure(entityId, messageId, page, size)
            .map(ResponseEntity::ok);
    }
}
