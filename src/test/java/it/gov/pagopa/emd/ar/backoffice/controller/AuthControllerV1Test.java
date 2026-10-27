package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.controller.AuthControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakUserDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.PageResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.auth.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import static org.mockito.ArgumentMatchers.*;

import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;

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
    void getUsers_ShouldReturnPaginatedList() {
        KeycloakUserDTOV1 user = KeycloakUserDTOV1.builder()
                .id("12345")
                .firstName("Mario")
                .lastName("Rossi")
                .email("mario@example.com")
                .build();

        PageResponseDTOV1 pageResponse = PageResponseDTOV1.builder()
                .content(List.of(user))
                .totalElements(1)
                .first(0)
                .max(5)
                .build();

        Mockito.when(authService.getUsers(eq("Mario"), eq(0), eq(5)))
                .thenReturn(Mono.just(ResponseEntity.ok(pageResponse)));

        webTestClient.get()
                .uri(builder -> builder.path("/emd/backoffice/api/v1/users")
                        .queryParam("search", "Mario")
                        .queryParam("first", 0)
                        .queryParam("max", 5)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.first").isEqualTo(0)
                .jsonPath("$.max").isEqualTo(5)
                .jsonPath("$.content[0].firstName").isEqualTo("Mario")
                .jsonPath("$.content[0].email").isEqualTo("mario@example.com");
    }

    @Test
    void getUsers_ShouldUseDefaultPagination_WhenParamsAreMissing() {
        PageResponseDTOV1 emptyPage = PageResponseDTOV1.builder()
                .content(List.of())
                .totalElements(0)
                .first(0)
                .max(5)
                .build();

        Mockito.when(authService.getUsers(isNull(), eq(0), eq(5)))
                .thenReturn(Mono.just(ResponseEntity.ok(emptyPage)));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.first").isEqualTo(0)
                .jsonPath("$.max").isEqualTo(5);
    }

    @Test
    void getUsers_ShouldReturn500_WhenServiceFails() {
        Mockito.when(authService.getUsers(any(), any(), any()))
                .thenReturn(Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/users")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}