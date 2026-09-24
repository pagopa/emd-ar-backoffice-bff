package it.gov.pagopa.emd.ar.backoffice.service.message;

import com.azure.monitor.query.logs.LogsQueryAsyncClient;
import com.azure.monitor.query.logs.models.LogsBatchQuery;
import com.azure.monitor.query.logs.models.LogsBatchQueryResult;
import com.azure.monitor.query.logs.models.LogsBatchQueryResultCollection;
import com.azure.monitor.query.logs.models.LogsTable;
import com.azure.monitor.query.logs.models.LogsTableCell;
import com.azure.monitor.query.logs.models.LogsTableRow;
import it.gov.pagopa.emd.ar.backoffice.enums.AzureSeverity;
import it.gov.pagopa.emd.ar.backoffice.service.azure.AzureServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AzureServiceImplTest {

    @Mock
    private LogsQueryAsyncClient logsQueryClientMock;

    private AzureServiceImpl azureService;
    private final String workspaceId = "test-workspace-123";

    @BeforeEach
    void setUp() {
        azureService = new AzureServiceImpl(workspaceId);

        // Sostituiamo il client reale con il nostro mock per testare la logica offline.
        ReflectionTestUtils.setField(azureService, "logsQueryClient", logsQueryClientMock);
    }

    // ── fetchLogsFromAzure ────────────────────────────────────────────────────────

    /**
     * Test controllo difensivo: Input vuoti o null.
     * Il metodo deve ritornare un DTO vuoto senza chiamare Azure.
     */
    @Test
    void fetchLogsFromAzure_InvalidInputs_ReturnsEmptyDtoAndSkipsAzureCall() {
        String entityId = "   ";
        String messageId = null;
        int page = 0;
        int size = 10;

        StepVerifier.create(azureService.fetchLogsFromAzure(entityId, messageId, page, size))
                .assertNext(result -> {
                    assertTrue(result.getContent().isEmpty());
                    assertEquals(0, result.getTotalElements());
                })
                .verifyComplete();

        // Verifica che il client Azure non sia mai stato chiamato (ora usa queryBatch)
        verify(logsQueryClientMock, never()).queryBatch(any(LogsBatchQuery.class));
    }

    /**
     * Nessun log trovato su Azure (tabelle vuote all'interno del batch).
     */
    @Test
    void fetchLogsFromAzure_NoDataFound_ReturnsEmptyDto() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        // Mock del risultato Batch che conterrà tabelle vuote
        LogsBatchQueryResult emptyResult = mock(LogsBatchQueryResult.class);
        LogsBatchQueryResultCollection batchCollectionMock = mock(LogsBatchQueryResultCollection.class);
        
        when(batchCollectionMock.getResult(anyString())).thenReturn(emptyResult);
        when(logsQueryClientMock.queryBatch(any(LogsBatchQuery.class)))
                .thenReturn(Mono.just(batchCollectionMock));

        StepVerifier.create(azureService.fetchLogsFromAzure(entityId, messageId, 0, 10))
                .assertNext(result -> {
                    assertTrue(result.getContent().isEmpty());
                    assertEquals(0, result.getTotalElements());
                    assertEquals(0, result.getTotalPages());
                })
                .verifyComplete();

        verify(logsQueryClientMock, times(1)).queryBatch(any(LogsBatchQuery.class));
    }

    /**
     * Happy path: Dati presenti. Verifica mappatura corretta dei campi, 
     * appName e conversione Severity.
     */
    @Test
    void fetchLogsFromAzure_DataFound_MapsLogsSuccessfully() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";
        int page = 0;
        int size = 10;

        LogsBatchQueryResult dataResult = mockDataResult(
                "2026-10-01T10:00:00Z",
                "Messaggio di test",
                "1", // 1 = INFO in AzureSeverity
                "message-core-service"
        );

        LogsBatchQueryResult countResult = mockCountResult(5L);

        LogsBatchQueryResultCollection batchCollectionMock = mock(LogsBatchQueryResultCollection.class);
        
        // Il codice chiama getResult prima per i dati, poi per il count.
        when(batchCollectionMock.getResult(anyString()))
                .thenReturn(dataResult)
                .thenReturn(countResult);

        when(logsQueryClientMock.queryBatch(any(LogsBatchQuery.class)))
                .thenReturn(Mono.just(batchCollectionMock));
                
        StepVerifier.create(azureService.fetchLogsFromAzure(entityId, messageId, page, size))
                .assertNext(result -> {
                    // Verifica Paginazione
                    assertEquals(0, result.getPage());
                    assertEquals(10, result.getSize());
                    assertEquals(5, result.getTotalElements());
                    assertEquals(1, result.getTotalPages()); // ceil(5/10) = 1
                    
                    // Verifica Contenuto
                    assertEquals(1, result.getContent().size());
                    assertEquals("2026-10-01T10:00:00Z", result.getContent().get(0).getTimestamp());
                    assertEquals("Messaggio di test", result.getContent().get(0).getMessage());
                    assertEquals("message-core-service", result.getContent().get(0).getAppName());
                    assertEquals(AzureSeverity.INFO.name(), result.getContent().get(0).getLevel());
                })
                .verifyComplete();
    }

    /**
     * Errore di rete / Timeout dal client Azure.
     */
    @Test
    void fetchLogsFromAzure_AzureClientError_PropagatesError() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        // Simuliamo un'eccezione lanciata dal client Azure (es. timeout di rete)
        when(logsQueryClientMock.queryBatch(any(LogsBatchQuery.class)))
                .thenReturn(Mono.error(new RuntimeException("Azure Monitor timeout")));
                
        // Verifichiamo che la catena reattiva termini con un Errore e non con un risultato
        StepVerifier.create(azureService.fetchLogsFromAzure(entityId, messageId, 0, 10))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Azure Monitor timeout")
                )
                .verify();
    }

    // ── Metodi di Utility per mockare le risposte complesse dell'SDK di Azure ───────

    private LogsBatchQueryResult mockCountResult(long countValue) {
        LogsBatchQueryResult result = mock(LogsBatchQueryResult.class);
        LogsTable table = mock(LogsTable.class);
        LogsTableRow row = mock(LogsTableRow.class);
        LogsTableCell cell = mock(LogsTableCell.class);
        
        when(result.getTable()).thenReturn(table);
        when(table.getRows()).thenReturn(List.of(row));
        
        // Simula l'estrazione del count
        when(row.getColumnValue("Count")).thenReturn(Optional.of(cell));
        when(cell.getValueAsString()).thenReturn(String.valueOf(countValue));
        return result;
    }

    private LogsBatchQueryResult mockDataResult(String timestamp, String message, String severity, String appRoleName) {
        LogsBatchQueryResult result = mock(LogsBatchQueryResult.class);
        LogsTable table = mock(LogsTable.class);
        LogsTableRow row = mock(LogsTableRow.class);
        
        // Mock celle
        LogsTableCell timeCell = mock(LogsTableCell.class);
        when(timeCell.getValueAsString()).thenReturn(timestamp);
        
        LogsTableCell msgCell = mock(LogsTableCell.class);
        when(msgCell.getValueAsString()).thenReturn(message);
        
        LogsTableCell sevCell = mock(LogsTableCell.class);
        when(sevCell.getValueAsString()).thenReturn(severity);
        
        LogsTableCell appCell = mock(LogsTableCell.class);
        when(appCell.getValueAsString()).thenReturn(appRoleName);
        
        when(row.getColumnValue("TimeGenerated")).thenReturn(Optional.of(timeCell));
        when(row.getColumnValue("Message")).thenReturn(Optional.of(msgCell));
        when(row.getColumnValue("SeverityLevel")).thenReturn(Optional.of(sevCell));
        when(row.getColumnValue("AppRoleName")).thenReturn(Optional.of(appCell));
        
        when(result.getTable()).thenReturn(table);
        when(table.getRows()).thenReturn(List.of(row));
        return result;
    }
}