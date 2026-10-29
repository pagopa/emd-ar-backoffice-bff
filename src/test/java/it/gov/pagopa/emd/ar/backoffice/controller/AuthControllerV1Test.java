package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.controller.AuthControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.auth.AuthServiceImpl;

import static org.mockito.ArgumentMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AuthControllerV1Test {

    private AuthServiceImpl authService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthServiceImpl.class);
        // Bindiamo il controller manualmente al WebTestClient
        webTestClient = WebTestClient.bindToController(new AuthControllerImplV1(authService)).build();
    }

    @Test
    void exchangeToken_ShouldReturnOk() {
        AuthRequestDTOV1 request = new AuthRequestDTOV1("valid-token");
        AuthResponseV1 responseBody = AuthResponseV1.builder().token("abc").build();

        Mockito.when(authService.exchangeToken(anyString()))
                .thenReturn(Mono.just(ResponseEntity.ok(responseBody)));

        webTestClient.post()
                .uri("/emd/backoffice/api/v1/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("abc");
    }

    @Test
    void getAvailableRoles_ShouldReturnOk() {
        KeycloakRoleDTOV1 role = new KeycloakRoleDTOV1("id-123", "operator-read", "desc");

        Mockito.when(authService.getAvailableRoles())
                .thenReturn(Mono.just(ResponseEntity.ok(List.of(role))));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/roles")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("id-123")
                .jsonPath("$[0].name").isEqualTo("operator-read");
    }

    @Test
    void replaceUserRoles_ShouldReturnNoContent() {
        KeycloakRoleDTOV1 role = new KeycloakRoleDTOV1("id-123", "operator-write", "desc");
        String userId = "user-uuid-123";

        Mockito.when(authService.replaceUserRoles(anyString(), any()))
                .thenReturn(Mono.just(ResponseEntity.noContent().build()));

        webTestClient.post()
                .uri("/emd/backoffice/api/v1/users/{userId}/roles", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(role))
                .exchange()
                .expectStatus().isNoContent() // Ci aspettiamo 204
                .expectBody().isEmpty();
    }
}