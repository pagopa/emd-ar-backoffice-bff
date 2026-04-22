package it.gov.pagopa.emd.ar.backoffice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.UserDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.AuthServiceImpl;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {
    
    @Mock
    private WebClient webClient;

    
    // Mock per la catena fluida del WebClient
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private AuthServiceImpl backofficeService;

    @BeforeEach
    void setUp() {
        // Iniezione manuale o con @InjectMocks (ma WebClient richiede un setup particolare)
        backofficeService = new AuthServiceImpl(webClient, null, null); // Passa i mock necessari
        // Setup base per simulare @Value
        ReflectionTestUtils.setField(backofficeService, "authServerUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(backofficeService, "realm", "mdc");
    }

    @Test
    void whenGetTokenSuccess_thenReturnsResponseDTO() {
        // 1. Mock authService.verifyTokenFields
        UserDTOV1 user = new UserDTOV1(); // Popola con dati mock
        //when(authService.verifyTokenFields(any())).thenReturn(user);

        // 2. Mock WebClient per getKeycloakAccessToken
        // NB: Mockare WebClient è complesso, spesso si usa MockWebServer 
        // o si crea un helper per mockare la catena .post().uri()...
        
        // 3. Esempio d'uso di StepVerifier
        //Mono<ResponseEntity<ResponseDTO>> result = backofficeService.getToken(mock(Jwt.class));

        /*StepVerifier.create(result)
            .assertNext(response -> {
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertEquals("Success", response.getBody().getStatus());
            })
            .verifyComplete();*/
        }
}
