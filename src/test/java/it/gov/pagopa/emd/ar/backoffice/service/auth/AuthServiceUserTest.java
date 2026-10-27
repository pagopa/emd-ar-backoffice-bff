package it.gov.pagopa.emd.ar.backoffice.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakUserDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.PageResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakTokenService;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class AuthServiceUserTest {
    @Mock
    private SelfCareTokenValidator selfCareValidator;
    @Mock
    private KeycloakTokenService tokenService;
    @Mock
    private KeycloakUserService userService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    /**
     * Happy path: Il servizio ottiene con successo il manager token e
     * recupera la lista paginata degli utenti da Keycloak.
     */
    @Test
    void getUsers_Success() {
        String mockManagerToken = "ey.mock.manager.token";
        String searchParam = "Mario";
        
        KeycloakUserDTOV1 mockUser = KeycloakUserDTOV1.builder()
                .firstName("Mario")
                .email("mario@example.com")
                .build();
                
        PageResponseDTOV1 mockPage = PageResponseDTOV1.builder()
                .content(List.of(mockUser))
                .totalElements(1)
                .first(0)
                .max(5)
                .build();

        when(tokenService.getManagerToken()).thenReturn(Mono.just(mockManagerToken));
        when(userService.getPaginatedUsersWithRoles(mockManagerToken, searchParam, 0, 5))
                .thenReturn(Mono.just(mockPage));

        StepVerifier.create(authService.getUsers(searchParam, 0, 5))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    
                    PageResponseDTOV1 body = response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.getTotalElements());
                    assertEquals(0, body.getFirst());
                    assertEquals("Mario", body.getContent().get(0).getFirstName());
                })
                .verifyComplete();
    }

    /**
     * Errore: Keycloak è giù o c'è un errore nel recupero del Manager Token.
     * Il service deve catturare l'errore e restituire HTTP 500.
     */
    @Test
    void getUsers_FailsOnManagerToken_ReturnsInternalServerError() {
        when(tokenService.getManagerToken())
                .thenReturn(Mono.error(new RuntimeException("Unable to fetch manager token")));

        StepVerifier.create(authService.getUsers(null, 0, 5))
                .assertNext(response -> {
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                })
                .verifyComplete();
        
        Mockito.verifyNoInteractions(userService);
    }

    /**
     * Errore: Il Manager token viene recuperato, ma la chiamata per recuperare 
     * la lista paginata degli utenti fallisce.
     * Il service deve catturare l'errore e restituire HTTP 500.
     */
    @Test
    void getUsers_FailsOnFetchingUsers_ReturnsInternalServerError() {
        String mockManagerToken = "ey.mock.manager.token";
        
        when(tokenService.getManagerToken()).thenReturn(Mono.just(mockManagerToken));
        
        when(userService.getPaginatedUsersWithRoles(eq(mockManagerToken), isNull(), eq(0), eq(10)))
                .thenReturn(Mono.error(new RuntimeException("Keycloak API error")));

        StepVerifier.create(authService.getUsers(null, 0, 10))
                .assertNext(response -> {
                    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                })
                .verifyComplete();
    }
}
