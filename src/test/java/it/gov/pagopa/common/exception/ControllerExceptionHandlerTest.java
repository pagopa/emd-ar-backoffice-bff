package it.gov.pagopa.common.exception;

import it.gov.pagopa.common.utils.TestUtils;
import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.handler.ControllerExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControllerExceptionHandlerTest {

    private WebTestClient webTestClient;

    @Mock
    private Utilities utilities;

    @Mock
    private TestController testControllerMock;

    private final String traceId = "TRACEID";

    @RestController
    static class TestController {
        @PostMapping(value = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
        public Mono<String> testEndpoint(@RequestParam("data") String data, @Valid @RequestBody TestRequestBody body) {
            return Mono.just("OK");
        }
    }

    @BeforeEach
    void setUp() {
        TestUtils.clearDefaultTimezone();

        // Configuriamo il WebTestClient agganciando il controller e l'handler manualmente
        // Evita errori di caricamento del contesto Spring
        this.webTestClient = WebTestClient.bindToController(testControllerMock)
                .controllerAdvice(new ControllerExceptionHandler(utilities))
                .configureClient()
                .responseTimeout(Duration.ofSeconds(10))
                .build();

        Mockito.lenient().when(utilities.getTraceId()).thenReturn(traceId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestRequestBody {
        @NotNull private String requiredField;
        private String notRequiredField;
        @Pattern(regexp = "[a-z]+") private String lowerCaseAlphabeticField;
        private LocalDateTime dateTimeField;
    }

    @Test
    void handleMissingRequestParameterException() {
        // Test di un parametro mancante (gestito da Spring prima di arrivare al mock)
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").build()) // Manca 'data'
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TestRequestBody("val", null, "abc", LocalDateTime.now()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> Assertions.assertThat((String) value)
                                                                .contains("Required query parameter 'data' is not present"));
    }

    @Test
    void handleRuntimeExceptionError() {
        // Configurazione del mock per lanciare errore
        when(testControllerMock.testEndpoint(any(), any())).thenReturn(Mono.error(new RuntimeException("Error")));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TestRequestBody("val", null, "abc", LocalDateTime.now()))
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GENERIC_ERROR")
                // The internal exception message is NOT returned to the client (security: avoids leaking internals)
                .jsonPath("$.message").isEqualTo("An unexpected error occurred. Please retry or contact support.");
    }

    @Test
    void handleViolationException() {
        // Mock di una violazione di validazione manuale
        ConstraintViolation<?> violation = Mockito.mock(ConstraintViolation.class);
        jakarta.validation.Path path = Mockito.mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn("fieldName");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("resolved message");

        ConstraintViolationException ex = new ConstraintViolationException("Error", Set.of(violation));
        when(testControllerMock.testEndpoint(any(), any())).thenReturn(Mono.error(ex));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TestRequestBody("val", null, "abc", LocalDateTime.now()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo("Invalid request content. fieldName: resolved message");
    }

    @Test
    void handleUrlNotFound() {
        webTestClient.get()
                .uri("/url-non-esistente")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void handleResponseStatusException() {
        when(testControllerMock.testEndpoint(any(), any()))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, "Custom Error")));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TestRequestBody("val", null, "abc", LocalDateTime.now()))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.EXPECTATION_FAILED)
                .expectBody()
                .jsonPath("$.message").value(value -> Assertions.assertThat((String) value)
                                                                .contains("Custom Error"));
    }

    @Test
    void handleNotParsableBodyException() {
        // Quando Jackson fallisce il parsing in WebFlux, il messaggio di default è "Failed to read HTTP message"
        String malformedJson = "{\"requiredField\":\"val\", \"dateTimeField\":\"2025-99-99\"}";

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> Assertions.assertThat((String) value)
                                                                .contains("Failed to read HTTP message"));
    }

    @Test
    void handleMalformedJsonException() {
        String malformedJson = "{\"requiredField\":\"val\" ";

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> Assertions.assertThat((String) value)
                                                                .contains("Failed to read HTTP message"));
    }

    @Test
    void handleNoBodyException() {
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> Assertions.assertThat((String) value)
                                                                .contains("No request body"));
    }

    @Test
    void handleUnsupportedMediaType() {
        // Test quando il client invia un Content-Type non supportato
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .contentType(MediaType.TEXT_XML) // Invia XML, il controller si aspetta JSON
                .bodyValue("<xml>test</xml>")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }

    @Test
    void handleMethodNotAllowed() {
        // Test quando si sbaglia il metodo HTTP (es. GET invece di POST)
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/test").queryParam("data", "val").build())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }
}
