package it.gov.pagopa.common.exception;

import it.gov.pagopa.common.config.json.JsonConfig;
import it.gov.pagopa.common.utils.TestUtils;
import it.gov.pagopa.common.utils.Utilities; // Importa la classe Utilities reale
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.internal.engine.ConstraintViolationImpl;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // O @MockBean se usi Spring Boot < 3.4
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith({SpringExtension.class})
//@WebFluxTest(controllers = {ControllerExceptionHandlerTest.TestController.class})
@ContextConfiguration(classes = {
        ControllerExceptionHandlerTest.TestController.class,
        ControllerExceptionHandler.class,
        JsonConfig.class
})
class ControllerExceptionHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoSpyBean
    private TestController testControllerSpy;

    // MODIFICA: Mockiamo Utilities invece di usare MDC
    @MockitoBean
    private Utilities utilities;

    public static final String DATA = "data";
    public static final TestRequestBody BODY = new TestRequestBody("bodyData", null, "abc", LocalDateTime.now());
    private final String traceId = "TRACEID";

    @RestController
    @Slf4j
    static class TestController {
        @PostMapping(value = "/test", produces = MediaType.APPLICATION_JSON_VALUE)
        Mono<String> testEndpoint(@RequestParam("data") String data, @Valid @RequestBody TestRequestBody body) {
            return Mono.just("OK");
        }
    }

    @BeforeEach
    void init() {
        TestUtils.clearDefaultTimezone();
        // Configura il mock per restituire il traceId fisso durante i test
        Mockito.when(utilities.getTraceId()).thenReturn(traceId);
    }

    // Helper method per WebTestClient
    private WebTestClient.ResponseSpec performRequest(String data, MediaType accept, Object body) {
        WebTestClient.RequestBodySpec request = webTestClient.post()
                .uri(uriBuilder -> uriBuilder.path("/test")
                        .queryParam(DATA, data)
                        .build())
                .accept(accept);

        if (body != null) {
            return request.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange();
        }
        return request.exchange();
    }

    @Test
    void handleMissingRequestParameterException() {
        performRequest(null, MediaType.APPLICATION_JSON, BODY)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo("Required request parameter 'data' for method parameter type String is not present")
                .jsonPath("$.traceId").isEqualTo(traceId);
    }

    @Test
    void handleRuntimeExceptionError() {
        doThrow(new RuntimeException("Error")).when(testControllerSpy).testEndpoint(any(), any());

        performRequest(DATA, MediaType.APPLICATION_JSON, BODY)
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("GENERIC_ERROR")
                .jsonPath("$.message").isEqualTo("Error")
                .jsonPath("$.traceId").isEqualTo(traceId);
    }

    @Test
    void handleUrlNotFound() {
        webTestClient.post().uri("/NOTEXISTENTURL")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.traceId").isEqualTo(traceId);
    }

    @Test
    void handleNoBodyException() {
        performRequest(DATA, MediaType.APPLICATION_JSON, null)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo("Required request body is missing")
                .jsonPath("$.traceId").isEqualTo(traceId);
    }

    @Test
    void handleInvalidBodyException() {
        TestRequestBody invalidBody = new TestRequestBody(null, null, "ABC", null);

        performRequest(DATA, MediaType.APPLICATION_JSON, invalidBody)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("requiredField: must not be null"))
                .jsonPath("$.traceId").isEqualTo(traceId);
    }

    @Test
    void handleViolationException() {
        ConstraintViolationException ex = new ConstraintViolationException("Error", Set.of(
                ConstraintViolationImpl.forParameterValidation(
                        "template", Map.of(), Map.of(), "resolved message", null, null, null, null,
                        PathImpl.createPathFromString("fieldName"), null, null, null)
        ));

        doThrow(ex).when(testControllerSpy).testEndpoint(any(), any());

        performRequest(DATA, MediaType.APPLICATION_JSON, BODY)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo("Invalid request content. fieldName: resolved message")
                .jsonPath("$.traceId").isEqualTo(traceId);
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
}