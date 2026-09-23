package it.gov.pagopa.emd.ar.backoffice.service.message;

import com.azure.monitor.query.LogsQueryAsyncClient;
import com.azure.monitor.query.models.LogsQueryResult;
import com.azure.monitor.query.models.LogsTable;
import com.azure.monitor.query.models.LogsTableCell;
import com.azure.monitor.query.models.LogsTableRow;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AzureServiceImplTest {

    @Mock
    private LogsQueryAsyncClient logsQueryClientMock;

    private AzureServiceImpl azureService;

    private final String workspaceId = "test-workspace-123";

    @BeforeEach
    void setUp() {
        // 1. Instanziamo il service. Questo chiamerà "new LogsQueryClientBuilder()..."
        azureService = new AzureServiceImpl(workspaceId);

        // 2. MAGIA DELLA REFLECTION:
        // Sostituiamo il client reale (appena creato) con il nostro mock.
        // In questo modo, quando il service chiama "queryWorkspace", userà il mock e non la rete.
        ReflectionTestUtils.setField(azureService, "logsQueryClient", logsQueryClientMock);
    }

    // ── fetchLogsFromAzure ────────────────────────────────────────────────────────

    /**
     * Test controllo difensivo: Input vuoti o null. 
     * Il metodo deve ritornare un DTO vuoto senza chiamare Azure.
     */
    @Test
    void fetchLogsFromAzure_InvalidInputs_ReturnsEmptyDtoAndSkipsAzureCall() {
        String entityId = "   "; // Vuoto
        String messageId = null; // Null
        int page = 0;
        int size = 10;

        StepVerifier.create(azureService.fetchLogsFromAzure(entityId, messageId, page, size))
                .assertNext(result -> {
                    assertTrue(result.getContent().isEmpty());
                    assertEquals(0, result.getTotalElements());
                })
                .verifyComplete();

        // Verifica che il client Azure NON sia mai stato chiamato
        verify(logsQueryClientMock, never()).queryWorkspace(anyString(), anyString(), any());
    }

    /**
     * Nessun log trovato su Azure (tabelle vuote).
     */
    @Test
    void fetchLogsFromAzure_NoDataFound_ReturnsEmptyDto() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        // Mock risultati vuoti
        LogsQueryResult emptyResult = mock(LogsQueryResult.class);

        // Qualsiasi query venga passata (dati o count), rispondiamo con il mock vuoto
        when(logsQueryClientMock.queryWorkspace(eq(workspaceId), anyString(), isNull()))
                .thenReturn(Mono.just(emptyResult));

        StepVerifier.create(azureService.fetchLogsFromAzure(entityId, messageId, 0, 10))
                .assertNext(result -> {
                    assertTrue(result.getContent().isEmpty());
                    assertEquals(0, result.getTotalElements());
                    assertEquals(0, result.getTotalPages());
                })
                .verifyComplete();

        // 2 chiamate attese: una per i dati, una per il conteggio
        verify(logsQueryClientMock, times(2)).queryWorkspace(eq(workspaceId), anyString(), isNull());
    }

    /**
     * Happy path: Dati presenti. Verifica mappatura corretta dei campi e conversione Severity.
     */
    @Test
    void fetchLogsFromAzure_DataFound_MapsLogsSuccessfully() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";
        int page = 0;
        int size = 10;

        // 1. Mock Data Query Result (1 riga simulata)
        LogsQueryResult dataResult = mockDataResult(
                "2026-10-01T10:00:00Z", 
                "Messaggio di test", 
                "1", // 1 = INFO in AzureSeverity
                "trace-123"
        );

        // 2. Mock Count Query Result (Totale: 5 elementi)
        LogsQueryResult countResult = mockCountResult(5L);

        // Istruiamo Mockito: se la query finisce con "| count", restituisci countResult, altrimenti dataResult
        when(logsQueryClientMock.queryWorkspace(eq(workspaceId), argThat(query -> !query.contains("| count")), isNull()))
                .thenReturn(Mono.just(dataResult));
                
        when(logsQueryClientMock.queryWorkspace(eq(workspaceId), argThat(query -> query.contains("| count")), isNull()))
                .thenReturn(Mono.just(countResult));

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
                    assertEquals("trace-123", result.getContent().get(0).getTraceId());
                    assertEquals(AzureSeverity.INFO.name(), result.getContent().get(0).getLevel());
                })
                .verifyComplete();
    }

    /**
     * Errore di rete / Timeout dal client Azure. L'errore deve essere propagato.
     */
    @Test
    void fetchLogsFromAzure_AzureClientError_PropagatesError() {
        String entityId = "ENT-123";
        String messageId = "MSG-456";

        // Simuliamo un'eccezione lanciata dal client Azure
        when(logsQueryClientMock.queryWorkspace(eq(workspaceId), anyString(), isNull()))
                .thenReturn(Mono.error(new RuntimeException("Azure Monitor timeout")));
                
        StepVerifier.create(azureService.fetchLogsFromAzure(entityId, messageId, 0, 10))
                .expectErrorMatches(throwable -> 
                        throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Azure Monitor timeout"))
                .verify();
    }

    // ── Metodi di Utility per mockare le risposte complesse dell'SDK di Azure ───────

    private LogsQueryResult mockCountResult(long countValue) {
        LogsQueryResult result = mock(LogsQueryResult.class);
        LogsTable table = mock(LogsTable.class);
        LogsTableRow row = mock(LogsTableRow.class);
        LogsTableCell cell = mock(LogsTableCell.class);

        when(result.getTable()).thenReturn(table);
        when(table.getRows()).thenReturn(List.of(row));
        
        // Simula il comportamento della lambda nell'estrazione del count
        when(row.getColumnValue("Count")).thenReturn(Optional.of(cell));
        when(cell.getValueAsString()).thenReturn(String.valueOf(countValue));

        return result;
    }

    private LogsQueryResult mockDataResult(String timestamp, String message, String severity, String operationId) {
        LogsQueryResult result = mock(LogsQueryResult.class);
        LogsTable table = mock(LogsTable.class);
        LogsTableRow row = mock(LogsTableRow.class);

        // Mock celle
        LogsTableCell timeCell = mock(LogsTableCell.class);
        when(timeCell.getValueAsString()).thenReturn(timestamp);
        
        LogsTableCell msgCell = mock(LogsTableCell.class);
        when(msgCell.getValueAsString()).thenReturn(message);
        
        LogsTableCell sevCell = mock(LogsTableCell.class);
        when(sevCell.getValueAsString()).thenReturn(severity);
        
        LogsTableCell opCell = mock(LogsTableCell.class);
        when(opCell.getValueAsString()).thenReturn(operationId);

        when(row.getColumnValue("TimeGenerated")).thenReturn(Optional.of(timeCell));
        when(row.getColumnValue("Message")).thenReturn(Optional.of(msgCell));
        when(row.getColumnValue("SeverityLevel")).thenReturn(Optional.of(sevCell));
        when(row.getColumnValue("OperationId")).thenReturn(Optional.of(opCell));

        when(result.getTable()).thenReturn(table);
        when(table.getRows()).thenReturn(List.of(row));

        return result;
    }
}