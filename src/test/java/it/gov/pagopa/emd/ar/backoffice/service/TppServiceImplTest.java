package it.gov.pagopa.emd.ar.backoffice.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.gov.pagopa.emd.ar.backoffice.connector.v1.TppConnectorImplementationV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link TppServiceImpl}.
 * <p>
 * This class validates the service layer logic using Mockito for dependency isolation
 * and Project Reactor's {@link StepVerifier} to test reactive streams. It ensures 
 * the correct orchestration between TPP data persistence and Keycloak client management.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class TppServiceImplTest {
    
    @Mock
    private TppConnectorImplementationV1 tppConnector;

    @Mock
    private AuthServiceImpl authService;

    private TppServiceImpl tppService;

    /**
     * Initializes the service under test with mocked dependencies before each test execution.
     */
    @BeforeEach
    void setUp() {
        tppService = new TppServiceImpl(tppConnector, authService);
    }

    /**
     * Tests the successful scenario for TPP and Keycloak client creation.
     * <p>
     * Verifies that:
     * <ol>
     *     <li>The TPP is saved via the connector.</li>
     *     <li>The Keycloak client creation is triggered upon success.</li>
     *     <li>The final Mono emits the expected TPP ID and completes normally.</li>
     * </ol>
     * </p>
     */
    @Test
    void createTppAndKeycloakClient_Success() {
        // GIVEN: Prepare input DTO and mock successful responses
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Pagopa TPP");
        
        String savedTppId = "INTERNAL_ID_001";
        String keycloakClientId = "Pagopa TPP";

        when(tppConnector.saveTpp(any(TppDTOV1.class))).thenReturn(Mono.just(savedTppId));
        when(authService.createKeycloakClient(anyString())).thenReturn(Mono.just(keycloakClientId));

        // WHEN: Calling the orchestration method
        Mono<String> result = tppService.createTppAndKeycloakClient(dto);

        // THEN: Validate the reactive stream behavior and emitted values
        StepVerifier.create(result)
                .expectNext(savedTppId)
                .verifyComplete();

        // Verify that internal services were interacted with exactly once
        verify(tppConnector, times(1)).saveTpp(dto);
        verify(authService, times(1)).createKeycloakClient("Pagopa TPP");
    }

    /**
     * Tests the failure scenario when the TPP connector returns an error.
     * <p>
     * Verifies that the reactive chain is interrupted and the authentication service 
     * is never invoked if the initial TPP save fails, ensuring data consistency.
     * </p>
     */
    @Test
    void createTppAndKeycloakClient_ErrorOnConnector() {
        // GIVEN: Mock a failure in the persistence layer
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Fail TPP");

        when(tppConnector.saveTpp(any())).thenReturn(Mono.error(new RuntimeException("Database error")));

        // WHEN: Calling the orchestration method
        Mono<String> result = tppService.createTppAndKeycloakClient(dto);

        // THEN: Verify that the error is propagated and the stream terminates
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        // Verify that the Keycloak client creation was skipped due to the upstream error
        verify(authService, never()).createKeycloakClient(anyString());
    }

}
