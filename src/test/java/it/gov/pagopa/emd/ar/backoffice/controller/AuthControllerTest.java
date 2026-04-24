package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.controller.v1.AuthControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.service.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;

class AuthControllerImplV1Test {

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
}