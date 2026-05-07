package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
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
     * the connector persists it and returns a tppId, then the KC client is created.
     */
    @Test
    void createTppAndKeycloakClient_Success() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Pagopa TPP");

        String savedTppId = "INTERNAL_ID_001";

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId)).thenReturn(Mono.just(savedTppId));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectNext(savedTppId)
                .verifyComplete();

        verify(tppConnector, times(1)).saveTpp(any(TppCreateRequest.class));
        verify(keycloakClientService, times(1)).createKeycloakClient(savedTppId);
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
        when(keycloakClientService.createKeycloakClient(anyString())).thenReturn(Mono.just("tpp-id"));

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

        verify(keycloakClientService, never()).createKeycloakClient(anyString());
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

        String savedTppId = "tpp-123";
        RuntimeException kcException = new RuntimeException("Keycloak unavailable");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId)).thenReturn(Mono.error(kcException));
        when(tppConnector.deleteTpp(savedTppId)).thenReturn(Mono.empty());

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectError(RuntimeException.class)
                .verify();

        verify(tppConnector, times(1)).saveTpp(any(TppCreateRequest.class));
        verify(keycloakClientService, times(1)).createKeycloakClient(savedTppId);
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

        String savedTppId = "tpp-456";
        RuntimeException kcException = new RuntimeException("Keycloak unavailable");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId)).thenReturn(Mono.error(kcException));
        when(tppConnector.deleteTpp(savedTppId))
                .thenReturn(Mono.error(new RuntimeException("DB also down")));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectErrorMatches(ex -> ex.getMessage().equals("Keycloak unavailable"))
                .verify();

        verify(tppConnector, times(1)).deleteTpp(savedTppId);
    }
}
