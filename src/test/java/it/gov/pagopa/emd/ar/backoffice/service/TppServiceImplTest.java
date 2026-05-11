package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakClientService;
import it.gov.pagopa.emd.ar.backoffice.service.tpp.TppServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TppServiceImpl}.
 * <p>
 * Validates the service orchestration: TPP persistence happens first (obtaining the tppId),
 * followed by Keycloak client creation using that tppId as the client identifier.
 * The mapper (TppConnectorMapper) is NOT mocked — it is pure logic with no side effects.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class TppServiceImplTest {

    @Mock
    private TppConnector tppConnector;

    @Mock
    private KeycloakClientService keycloakClientService;

    private TppServiceImpl tppService;

    @BeforeEach
    void setUp() {
        tppService = new TppServiceImpl(tppConnector, keycloakClientService);
    }

    /**
     * Happy path: the mapper converts the API DTO to a TppCreateRequest,
     * the connector persists it and returns a tppId, then the KC client is created
     * with both tppId (as clientId) and entityId for the hardcoded-claim mapper.
     */
    @Test
    void createTppAndKeycloakClient_Success() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Pagopa TPP");
        dto.setEntityId("12345678901");

        String savedTppId = "INTERNAL_ID_001";

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId, "12345678901", "Pagopa TPP")).thenReturn(Mono.just(savedTppId));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectNext(savedTppId)
                .verifyComplete();

        verify(tppConnector, times(1)).saveTpp(any(TppCreateRequest.class));
        verify(keycloakClientService, times(1)).createKeycloakClient(savedTppId, "12345678901", "Pagopa TPP");
        verify(tppConnector, never()).deleteTpp(anyString());
    }

    /**
     * Input DTO is NOT mutated: the mapper creates a new object; the original stays unchanged.
     */
    @Test
    void createTppAndKeycloakClient_DoesNotMutateInputDto() {
        TppDTOV1 original = new TppDTOV1();
        original.setBusinessName("Test TPP");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just("tpp-id"));
        when(keycloakClientService.createKeycloakClient(anyString(), any(), any())).thenReturn(Mono.just("tpp-id"));

        tppService.createTppAndKeycloakClient(original).block();

        assert original.getIdPsp() == null : "Original DTO must not be mutated";
        assert original.getLegalAddress() == null : "Original DTO must not be mutated";
    }

    /**
     * Connector failure: if TPP persistence fails, Keycloak must never be called.
     */
    @Test
    void createTppAndKeycloakClient_ErrorOnConnector_KeycloakNeverCalled() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Fail TPP");

        when(tppConnector.saveTpp(any(TppCreateRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Database error")));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectError(RuntimeException.class)
                .verify();

        verify(keycloakClientService, never()).createKeycloakClient(anyString(), any(), any());
        verify(tppConnector, never()).deleteTpp(anyString());
    }

    /**
     * Keycloak failure after DB save: compensation kicks in (deleteTpp is called),
     * then the original Keycloak exception is propagated.
     */
    @Test
    void createTppAndKeycloakClient_ErrorOnKeycloak_CompensationTriggered() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("KC Fail TPP");
        dto.setEntityId("12345678901");

        String savedTppId = "tpp-123";
        RuntimeException kcException = new RuntimeException("Keycloak unavailable");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId, "12345678901", "KC Fail TPP")).thenReturn(Mono.error(kcException));
        when(tppConnector.deleteTpp(savedTppId)).thenReturn(Mono.empty());

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectError(RuntimeException.class)
                .verify();

        verify(tppConnector, times(1)).saveTpp(any(TppCreateRequest.class));
        verify(keycloakClientService, times(1)).createKeycloakClient(savedTppId, "12345678901", "KC Fail TPP");
        // Compensation must have been attempted
        verify(tppConnector, times(1)).deleteTpp(savedTppId);
    }

    /**
     * Double failure: Keycloak fails AND the compensating delete also fails.
     * The original Keycloak exception must still be propagated (not the delete exception).
     */
    @Test
    void createTppAndKeycloakClient_ErrorOnKeycloak_CompensationAlsoFails_OriginalErrorPropagated() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Double Fail TPP");
        dto.setEntityId("12345678901");

        String savedTppId = "tpp-456";
        RuntimeException kcException = new RuntimeException("Keycloak unavailable");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId, "12345678901", "Double Fail TPP")).thenReturn(Mono.error(kcException));
        when(tppConnector.deleteTpp(savedTppId))
                .thenReturn(Mono.error(new RuntimeException("DB also down")));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectErrorMatches(ex -> ex.getMessage().equals("Keycloak unavailable"))
                .verify();

        verify(tppConnector, times(1)).deleteTpp(savedTppId);
    }

    // ── deleteTppAndKeycloakClient ─────────────────────────────────────────────

    /**
     * Happy path: il client Keycloak viene eliminato, poi il TPP viene eliminato. Nessun errore.
     */
    @Test
    void deleteTppAndKeycloakClient_Success() {
        String tppId = "tpp-to-delete";

        when(keycloakClientService.deleteKeycloakClient(tppId)).thenReturn(Mono.empty());
        when(tppConnector.deleteTpp(tppId)).thenReturn(Mono.empty());

        StepVerifier.create(tppService.deleteTppAndKeycloakClient(tppId))
                .verifyComplete();

        verify(keycloakClientService, times(1)).deleteKeycloakClient(tppId);
        verify(tppConnector, times(1)).deleteTpp(tppId);
    }

    /**
     * Se la cancellazione Keycloak fallisce, l'errore si propaga e il connector NON viene chiamato.
     */
    @Test
    void deleteTppAndKeycloakClient_KeycloakFails_ErrorPropagated_ConnectorNotCalled() {
        String tppId = "tpp-kc-fail";
        RuntimeException kcException = new RuntimeException("Keycloak delete failed");

        when(keycloakClientService.deleteKeycloakClient(tppId)).thenReturn(Mono.error(kcException));

        StepVerifier.create(tppService.deleteTppAndKeycloakClient(tppId))
                .expectError(RuntimeException.class)
                .verify();

        verify(keycloakClientService, times(1)).deleteKeycloakClient(tppId);
        verify(tppConnector, never()).deleteTpp(anyString());
    }

    /**
     * Se Keycloak ha successo ma il connector fallisce, l'errore del connector si propaga.
     */
    @Test
    void deleteTppAndKeycloakClient_ConnectorFails_ErrorPropagated() {
        String tppId = "tpp-connector-fail";
        RuntimeException connectorException = new RuntimeException("TPP service unavailable");

        when(keycloakClientService.deleteKeycloakClient(tppId)).thenReturn(Mono.empty());
        when(tppConnector.deleteTpp(tppId)).thenReturn(Mono.error(connectorException));

        StepVerifier.create(tppService.deleteTppAndKeycloakClient(tppId))
                .expectErrorMatches(ex -> ex.getMessage().equals("TPP service unavailable"))
                .verify();

        verify(keycloakClientService, times(1)).deleteKeycloakClient(tppId);
        verify(tppConnector, times(1)).deleteTpp(tppId);
    }

    // ── getTppPagopaCredentials ────────────────────────────────────────────────

    /**
     * Happy path: il connector risolve il tppId dall'entityId, poi Keycloak restituisce le credenziali.
     * Verifica che le due chiamate avvengano in sequenza e che clientId e clientSecret siano corretti.
     */
    @Test
    void getTppPagopaCredentials_Success_ResolvesEntityIdThenFetchesCredentials() {
        String entityId = "12345678901";
        String tppId    = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";
        String secret   = "xYz123AbC456DeF789GhI012JkL345Mn";
        TppPagopaCredentialsDTOV1 expected = new TppPagopaCredentialsDTOV1(tppId, secret);

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.just(expected));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .assertNext(result -> {
                    assert result.getClientId().equals(tppId);
                    assert result.getClientSecret().equals(secret);
                })
                .verifyComplete();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
    }

    /**
     * TPP non trovata: il connector emette {@link ResourceNotFoundException}.
     * Keycloak NON deve essere chiamato.
     */
    @Test
    void getTppPagopaCredentials_TppNotFound_PropagatesResourceNotFoundException_KeycloakNeverCalled() {
        String entityId = "99999999999";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(entityId))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, never()).getPagopaClientCredentials(anyString());
    }

    /**
     * Il connector risolve il tppId ma il client Keycloak non esiste:
     * {@link ResourceNotFoundException} deve propagarsi.
     */
    @Test
    void getTppPagopaCredentials_KeycloakClientNotFound_PropagatesResourceNotFoundException() {
        String entityId = "12345678901";
        String tppId    = "tpp-orphan";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.error(new ResourceNotFoundException("Keycloak client", tppId)));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(tppId))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
    }

    /**
     * Keycloak non raggiungibile: {@link ExternalServiceException} deve propagarsi.
     */
    @Test
    void getTppPagopaCredentials_KeycloakUnavailable_PropagatesExternalServiceException() {
        String entityId = "12345678901";
        String tppId    = "tpp-kc-down";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.error(new ExternalServiceException("KEYCLOAK", "fetchClientSecret", "timeout")));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
    }

    /**
     * Verifica che il connector emd-tpp venga chiamato per la risoluzione del tppId
     * (a differenza del vecchio flusso dove il connector non era coinvolto).
     */
    @Test
    void getTppPagopaCredentials_AlwaysCallsConnectorToResolveTppId() {
        String entityId = "12345678901";
        String tppId    = "tpp-resolved";
        TppPagopaCredentialsDTOV1 credentials = new TppPagopaCredentialsDTOV1(tppId, "secret");

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.just(credentials));

        tppService.getTppPagopaCredentials(entityId).block();

        // Il connector DEVE essere chiamato (non è più bypass-ato)
        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        // Keycloak viene chiamato con il tppId risolto, NON con l'entityId
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
        verify(keycloakClientService, never()).getPagopaClientCredentials(entityId);
    }
}
