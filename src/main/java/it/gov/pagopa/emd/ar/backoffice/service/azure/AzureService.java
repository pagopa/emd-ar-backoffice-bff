package it.gov.pagopa.emd.ar.backoffice.service.azure;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.LogsResponseDTO;
import reactor.core.publisher.Mono;

public interface AzureService {
    
    public Mono<LogsResponseDTO> fetchLogsFromAzure(String entityId, String messageId, int page, int size);
}
