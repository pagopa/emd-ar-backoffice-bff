package it.gov.pagopa.emd.ar.backoffice.service.azure;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.LogsResponseDTO;
import reactor.core.publisher.Mono;

public interface AzureService {
    
    /**
     * Fetches a paginated list of logs from Azure Log Analytics based on the provided entity and message identifiers.
     * <p>
     * The method constructs and executes a complex KQL query that cross-references log entries
     * by their {@code OperationId} or by explicitly containing the {@code messageId} and {@code entityId}.
     * It performs two parallel asynchronous queries: one to retrieve the paginated data and another
     * to get the total count of matching records.
     * </p>
     *
     * @param entityId  the identifier of the entity (e.g., TPP ID)
     * @param messageId the identifier of the message to trace
     * @param page      the zero-based page index to retrieve
     * @param size      the maximum number of log entries per page
     * @return a {@link Mono} emitting a {@link LogsResponseDTO} containing the requested logs and pagination metadata
     */
    public Mono<LogsResponseDTO> fetchAllLogsFromAzure(String entityId, String messageId, int page, int size);
}
