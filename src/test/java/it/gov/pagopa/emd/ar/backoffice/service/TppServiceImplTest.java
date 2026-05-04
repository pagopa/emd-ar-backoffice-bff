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

@ExtendWith(MockitoExtension.class)
public class TppServiceImplTest {
    
    @Mock
    private TppConnectorImplementationV1 tppConnector;

    @Mock
    private AuthServiceImpl authService;

    private TppServiceImpl tppService;

    @BeforeEach
    void setUp() {
        tppService = new TppServiceImpl(tppConnector, authService);
    }

    @Test
    void createTppAndKeycloakClient_Success() {
        // Given
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Pagopa TPP");
        
        String savedTppId = "INTERNAL_ID_001";
        String keycloakClientId = "Pagopa TPP";

        when(tppConnector.saveTpp(any(TppDTOV1.class))).thenReturn(Mono.just(savedTppId));
        when(authService.createKeycloakClient(anyString())).thenReturn(Mono.just(keycloakClientId));

        // When
        Mono<String> result = tppService.createTppAndKeycloakClient(dto);

        // Then
        StepVerifier.create(result)
                .expectNext(savedTppId)
                .verifyComplete();

        // Verify interactions
        verify(tppConnector, times(1)).saveTpp(dto);
        verify(authService, times(1)).createKeycloakClient("Pagopa TPP");
    }

    @Test
    void createTppAndKeycloakClient_ErrorOnConnector() {
        // Given
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Fail TPP");

        when(tppConnector.saveTpp(any())).thenReturn(Mono.error(new RuntimeException("Database error")));

        // When
        Mono<String> result = tppService.createTppAndKeycloakClient(dto);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(authService, never()).createKeycloakClient(anyString());
    }

}
