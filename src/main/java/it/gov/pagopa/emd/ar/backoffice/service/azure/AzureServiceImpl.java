package it.gov.pagopa.emd.ar.backoffice.service.azure;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.monitor.query.logs.LogsQueryAsyncClient;
import com.azure.monitor.query.logs.LogsQueryClientBuilder;
import com.azure.monitor.query.logs.models.LogsBatchQuery;
import com.azure.monitor.query.logs.models.LogsBatchQueryResult;
import com.azure.monitor.query.logs.models.LogsQueryResult;
import com.azure.monitor.query.logs.models.LogsQueryTimeInterval;

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

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    /**
     * Maximum time interval in which logs are searched.
     */
    private static final LogsQueryTimeInterval QUERY_INTERVAL = new LogsQueryTimeInterval(Duration.ofDays(90));

    /**
     * Constructs a new {@code AzureServiceImpl}.
     * <p>
     * Initializes the asynchronous Azure logs client using default Azure credentials.
     * </p>
     *
     * @param workspaceId the Azure Log Analytics workspace identifier, injected via application properties
     */
    public AzureServiceImpl(@Value("${azure.monitor.workspace-id}") String workspaceId) {
        
        this.workspaceId = workspaceId;
        
        this.logsQueryClient = new LogsQueryClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<LogsResponseDTO> fetchAllLogsFromAzure(String entityId, String messageId, int page, int size) {
        log.info("[AR-BFF][AZURE_SEARCH] Searching messages — entityId {}, messageId={}", entityId, messageId);

        int normalizedPage = page < 0 ? DEFAULT_PAGE : page;
        int normalizedSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        long skip = (long) normalizedPage * normalizedSize;
        long takeUntil = skip + normalizedSize;
        
        if (messageId == null || messageId.isBlank() || entityId == null || entityId.isBlank()) {
            return Mono.just(LogsResponseDTO.builder()
                                            .content(List.of())
                                            .page(normalizedPage)
                                            .size(normalizedSize)
                                            .totalElements(0)
                                            .totalPages(0)
                                            .build());
        }

        String safeMessageId = escapeKqlString(messageId);
        String safeEntityId =  escapeKqlString(entityId);

        String baseQuery = buildFetchAllBaseQuery(safeMessageId, safeEntityId);

        String dataQuery = baseQuery
                + " | project TimeGenerated, Message, SeverityLevel, OperationId, AppRoleName "
                + " | order by TimeGenerated asc, OperationId asc "
                + " | serialize "
                + " | extend rn = row_number() "
                + String.format( " | where rn > %d and rn <= %d ", skip, takeUntil)
                + " | project-away rn";

        String countQuery = baseQuery + " | count";

        LogsBatchQuery batchQuery = new LogsBatchQuery();

        String dataQueryId = batchQuery.addWorkspaceQuery(workspaceId, dataQuery, QUERY_INTERVAL);
        String countQueryId = batchQuery.addWorkspaceQuery(workspaceId, countQuery, QUERY_INTERVAL);

        return logsQueryClient.queryBatch(batchQuery)
                .map(batchResult -> {
                    LogsBatchQueryResult dataResult = batchResult.getResult(dataQueryId);
                    LogsBatchQueryResult countResult = batchResult.getResult(countQueryId);

                    List<LogsDTO> mappedLogs = extractLogs(dataResult);
                    long totalElements = extractCount(countResult);
                    int totalPages = (int) Math.ceil((double)totalElements / normalizedSize);

                    return LogsResponseDTO.builder()
                            .content(mappedLogs)
                            .page(normalizedPage)
                            .size(normalizedSize)
                            .totalElements(totalElements)
                            .totalPages(totalPages)
                            .build();
                })
                .doOnSuccess(r -> log.info("[AR-BFF][MESSAGE_LOGS] Fetch completed: totalElements={}, totalPages={}", r.getTotalElements(), r.getTotalPages()))
                .doOnError(error -> log.error("Error querying Azure Monitor logs. entityId={}, messageId={}", entityId, messageId, error));

    }

    /**
     * Builds the common KQL query used by both the data and count queries.
     *
     * @param safeMessageId escaped message identifier
     * @param safeEntityId escaped entity identifier
     * @return base KQL query
     */
    private String buildFetchAllBaseQuery(String safeMessageId, String safeEntityId) {

        return """
                let targetMessageId = '%s';
                let targetEntityId = '%s';
                let targetOpId = toscalar(
                    AppTraces
                    | where Message has '[MESSAGE-CORE][SEND] Received message:' and Message has targetMessageId
                    | project OperationId
                    | take 1
                );
                AppTraces
                | where OperationId == targetOpId
                    or (Message has '[MESSAGE-CORE-CONSUMER-SERVICE]' and Message has targetMessageId)
                    or (Message has '[MESSAGE-SERVICE][SEND-NOTIFICATIONS]' and Message has targetMessageId)
                    or (Message has '[MESSAGE-SERVICE][HANDLE-ERROR]' and Message has targetMessageId)
                    or (Message has '[MESSAGE-SERVICE][ENQUEUE-WITH-RETRY]' and Message has targetMessageId)
                    or (Message has '[MESSAGE-SERVICE]' and Message has targetMessageId and Message has targetEntityId)
                    or (Message has '[MESSAGE-CORE-PRODUCER]' and Message has targetMessageId)
                    or (Message has '[MESSAGE-CORE-PRODUCER-SERVICE]' and Message has targetMessageId)
                    or (Message has '[NOTIFY-SERVICE]' and Message has targetMessageId and Message has targetEntityId)
                    or (Message has '[NOTIFY-ERROR-PRODUCER-SERVICE][ENQUEUE-NOTIFY]' and Message has targetMessageId and Message has targetEntityId)
                """
                    .formatted(safeMessageId, safeEntityId);
    }

    /**
     * Extracts and maps the raw tabular data from an Azure query result into a list of {@link LogsDTO}.
     * <p>
     * This method parses the {@code TimeGenerated}, {@code Message}, {@code AppRoleName},
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
                    row.getColumnValue("AppRoleName").ifPresent(cell -> itemBuilder.appName(cell.getValueAsString()));
                        
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

    /**
     * Escapes single quotes and backslashes in user input to prevent KQL injection.
     *
     * @param value the raw string parameter
     * @return the sanitized string safe for KQL injection
     */
    private String escapeKqlString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }

    
}