package it.gov.pagopa.emd.ar.backoffice.service.azure;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.monitor.query.LogsQueryAsyncClient;
import com.azure.monitor.query.LogsQueryClientBuilder;
import com.azure.monitor.query.models.LogsQueryResult;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.LogsDTO;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.LogsResponseDTO;
import it.gov.pagopa.emd.ar.backoffice.enums.AzureSeverity;
import reactor.core.publisher.Mono;

/**
 * Implementation of the {@link AzureService} interface.
 * <p>
 * This service is responsible for interacting with Azure Monitor Log Analytics.
 * It executes KQL (Kusto Query Language) queries to retrieve application traces
 * associated with specific messages and entities across various microservices.
 * </p>
 */
@Slf4j
@Service
public class AzureServiceImpl implements AzureService {
    
    private final LogsQueryAsyncClient logsQueryClient;
    private final String workspaceId;

    /**
     * Constructs a new {@code AzureServiceImpl}.
     * <p>
     * Initializes the asynchronous Azure logs client using default Azure credentials.
     * </p>
     *
     * @param workspaceId the Azure Log Analytics workspace identifier, injected via application properties
     */
    public AzureServiceImpl(@Value("${azure.monitor.workspace-id:3aa19e25-43a5-4694-97be-c5908d5385f1}") String workspaceId) {
        
        this.workspaceId = workspaceId;
        
        this.logsQueryClient = new LogsQueryClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
    }

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
    @Override
    public Mono<LogsResponseDTO> fetchLogsFromAzure(String entityId, String messageId, int page, int size) {
        
        String safeMessageId = messageId != null ? messageId.replace("'", "") : "";
        String safeEntityId = entityId != null ? entityId.replace("'", "") : "";
        
        if (safeMessageId.isBlank() || safeEntityId.isBlank()) {
            return Mono.just(LogsResponseDTO.builder().content(List.of()).build());
        }

        StringBuilder kqlBuilder = new StringBuilder();

        kqlBuilder.append("let targetOpId = toscalar( ");
        kqlBuilder.append("  AppTraces ");
        kqlBuilder.append(String.format("  | where Message contains '[MESSAGE-CORE][SEND] Received message: %s' ", safeMessageId));
        kqlBuilder.append("  | project OperationId ");
        kqlBuilder.append("  | take 1 ");
        kqlBuilder.append("); ");

        kqlBuilder.append("AppTraces ");
        
        kqlBuilder.append("| where Message contains '[MESSAGE-CORE]' ");
        kqlBuilder.append("     or Message contains '[MESSAGE-SERVICE]' ");
        kqlBuilder.append("     or Message contains '[NOTIFY-SERVICE]' ");

        kqlBuilder.append("| where OperationId == targetOpId ");
        kqlBuilder.append(String.format(" or (Message contains '[MESSAGE-CORE-CONSUMER-SERVICE]' and Message contains '%s') ", safeMessageId));
        kqlBuilder.append(String.format(" or (Message contains '[MESSAGE-CORE-PRODUCER]' and Message contains '%s') ", safeMessageId));
        kqlBuilder.append(String.format(" or (Message contains '[MESSAGE-CORE-PRODUCER-SERVICE]' and Message contains '%s') ", safeMessageId));

        
        kqlBuilder.append(String.format(" or (Message contains '[MESSAGE-SERVICE]' and Message contains '%s' and Message contains '%s') ", safeMessageId, safeEntityId));
        kqlBuilder.append(String.format(" or (Message contains '[NOTIFY-SERVICE]' and Message contains '%s' and Message contains '%s') ", safeMessageId, safeEntityId));

        
        
        String baseQuery = kqlBuilder.toString();

        int skip = page * size;
        int takeUntil = skip + size;

        String dataQuery = baseQuery +
                " | project TimeGenerated, Message, SeverityLevel, OperationId, AppRoleName " +
                " | order by TimeGenerated asc " +
                " | extend rn = row_number() " +
                String.format(" | where rn > %d and rn <= %d ", skip, takeUntil) +
                " | project-away rn";

        String countQuery = baseQuery + " | count";

        Mono<LogsQueryResult> dataMono = logsQueryClient.queryWorkspace(workspaceId, dataQuery, null);
        Mono<LogsQueryResult> countMono = logsQueryClient.queryWorkspace(workspaceId, countQuery, null);

        return Mono.zip(dataMono, countMono)
                .map(tuple -> {
                    LogsQueryResult dataResult = tuple.getT1();
                    LogsQueryResult countResult = tuple.getT2();

                    List<LogsDTO> mappedLogs = extractLogs(dataResult);
                    long totalElements = extractCount(countResult);
                    int totalPages = (int) Math.ceil((double) totalElements / size);

                    return LogsResponseDTO.builder()
                            .content(mappedLogs)
                            .page(page)
                            .size(size)
                            .totalElements(totalElements)
                            .totalPages(totalPages)
                            .build();
                });
    }

    /**
     * Extracts and maps the raw tabular data from an Azure query result into a list of {@link LogsDTO}.
     * <p>
     * This method parses the {@code TimeGenerated}, {@code Message}, {@code OperationId} (mapped to traceId),
     * and {@code SeverityLevel} columns. The severity level is safely converted into the corresponding
     * {@link AzureSeverity} string representation.
     * </p>
     *
     * @param result the {@link LogsQueryResult} returned by the Azure data query
     * @return a list of {@link LogsDTO} objects, or an empty list if the result is null or contains no rows
     */
    private List<LogsDTO> extractLogs(LogsQueryResult result) {
        if (result.getTable() == null || result.getTable().getRows() == null) {
            return List.of();
        }

        return result.getTable().getRows().stream()
                .map(row -> {
                    LogsDTO.LogsDTOBuilder itemBuilder = LogsDTO.builder();
                    
                    row.getColumnValue("TimeGenerated").ifPresent(cell -> itemBuilder.timestamp(cell.getValueAsString()));
                    row.getColumnValue("Message").ifPresent(cell -> itemBuilder.message(cell.getValueAsString()));
                    row.getColumnValue("OperationId").ifPresent(cell -> itemBuilder.traceId(cell.getValueAsString()));
                        
                    row.getColumnValue("SeverityLevel").ifPresent(cell -> {
                        String severityStr = cell.getValueAsString();
                        if (severityStr != null && !severityStr.isBlank()) {
                            try {
                                int severity = Integer.parseInt(severityStr);
                                itemBuilder.level(AzureSeverity.fromCode(severity).name());
                            } catch (NumberFormatException e) {
                                itemBuilder.level(AzureSeverity.UNKNOWN.name());
                            }
                        }
                    });
                    
                    return itemBuilder.build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Extracts the total record count from the Azure count query result.
     * <p>
     * It safely attempts to read and parse the "Count" column from the first row of the result.
     * </p>
     *
     * @param countResult the {@link LogsQueryResult} returned by the Azure count query
     * @return the total number of matching logs, or {@code 0L} if the result is empty or parsing fails
     */
    private long extractCount(LogsQueryResult countResult) {
        if (countResult.getTable() != null && countResult.getTable().getRows() != null && !countResult.getTable().getRows().isEmpty()) {
            return countResult.getTable().getRows().get(0).getColumnValue("Count")
                    .map(cell -> {
                        try {
                            return Long.parseLong(cell.getValueAsString());
                        } catch (Exception e) {
                            return 0L;
                        }
                    }).orElse(0L);
        }
        return 0L;
    }
}