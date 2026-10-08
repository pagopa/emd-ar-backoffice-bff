package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.citizen.CitizenConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CitizenConsentConnectorTest {

    private static final String BASE_URL = "http://emd-citizen.test";
    private static final String SEARCH_PATH = "/emd/citizen/consent/search";
    private static final String FISCAL_CODE = "RSSMRA85T10A562S";

    @Test
    void postsFiscalCodeInJsonBodyAndDeserializesEnrichedConsentMapAndLocalDateTime() {
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        String responseJson = """
                {
                  "fiscalCode": "RSSMRA85T10A562S",
                  "consents": {
                    "TPP_XYZ_123": {
                      "tppState": true,
                      "tcDate": "2025-10-17T13:18:37.313",
                      "entityId": "entity-123",
                      "businessName": "Nome Azienda TPP"
                    }
                  }
                }
                """;
        CitizenConnectorImpl connector = connectorWith(request -> {
            capturedUrl.set(request.url().toString());
            capturedMethod.set(request.method());
            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(mockRequest, ExchangeStrategies.withDefaults())
                    .doOnSuccess(ignored -> capturedBody.set(mockRequest.getBodyAsString().block()))
                    .thenReturn(jsonResponse(HttpStatus.OK, responseJson));
        });

        StepVerifier.create(connector.searchCitizenConsents(FISCAL_CODE))
                .assertNext(response -> {
                    assertThat(response.getFiscalCode()).isEqualTo(FISCAL_CODE);
                    assertThat(response.getConsents()).containsOnlyKeys("TPP_XYZ_123");
                    var consent = response.getConsents().get("TPP_XYZ_123");
                    assertThat(consent.getTppState()).isTrue();
                    assertThat(consent.getTcDate()).isEqualTo(LocalDateTime.parse("2025-10-17T13:18:37.313"));
                    assertThat(consent.getEntityId()).isEqualTo("entity-123");
                    assertThat(consent.getBusinessName()).isEqualTo("Nome Azienda TPP");
                })
                .verifyComplete();

        assertThat(capturedMethod.get()).isEqualTo(HttpMethod.POST);
        assertThat(capturedUrl.get()).isEqualTo(BASE_URL + SEARCH_PATH);
        assertThat(capturedUrl.get()).doesNotContain(FISCAL_CODE, "?");
        assertThat(capturedBody.get()).contains("\"fiscalCode\":\"RSSMRA85T10A562S\"");
    }

    @Test
    void emptyConsentsDeserializeAsAnEmptyMap() {
        CitizenConnectorImpl connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.OK,
                "{\"fiscalCode\":\"RSSMRA85T10A562S\",\"consents\":{}}")));

        StepVerifier.create(connector.searchCitizenConsents(FISCAL_CODE))
                .assertNext(response -> assertThat(response.getConsents()).isEmpty())
                .verifyComplete();
    }

    @Test
    void upstreamBadRequestMapsToSanitizedValidationException() {
        CitizenConnectorImpl connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.BAD_REQUEST,
                "{\"code\":\"BAD_REQUEST\",\"message\":\"sensitive detail\"}")));

        StepVerifier.create(connector.searchCitizenConsents(FISCAL_CODE))
                .expectErrorMatches(error -> error instanceof ValidationException
                        && !error.getMessage().contains("sensitive detail"))
                .verify();
    }

    @Test
    void citizenNotFoundMapsToBffResourceNotFound() {
        CitizenConnectorImpl connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.NOT_FOUND,
                "{\"code\":\"CITIZEN_NOT_ONBOARDED\",\"message\":\"private detail\"}")));

        StepVerifier.create(connector.searchCitizenConsents(FISCAL_CODE))
                .expectErrorMatches(error -> error instanceof ResourceNotFoundException
                        && !error.getMessage().contains("private detail"))
                .verify();
    }

    @Test
    void upstreamServerErrorMapsToSanitizedExternalServiceException() {
        CitizenConnectorImpl connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"code\":\"GENERIC_ERROR\",\"message\":\"private detail\"}")));

        StepVerifier.create(connector.searchCitizenConsents(FISCAL_CODE))
                .expectErrorMatches(error -> error instanceof ExternalServiceException
                        && !error.getMessage().contains("private detail"))
                .verify();
    }

    @Test
    void networkAndDecodeErrorsMapToExternalServiceException() {
        CitizenConnectorImpl networkFailure = connectorWith(request -> Mono.error(new IllegalStateException("private network detail")));
        StepVerifier.create(networkFailure.searchCitizenConsents(FISCAL_CODE))
                .expectError(ExternalServiceException.class)
                .verify();

        CitizenConnectorImpl decodeFailure = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.OK, "not-json")));
        StepVerifier.create(decodeFailure.searchCitizenConsents(FISCAL_CODE))
                .expectError(ExternalServiceException.class)
                .verify();
    }

    private static ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static CitizenConnectorImpl connectorWith(ExchangeFunction exchangeFunction) {
        return new CitizenConnectorImpl(WebClient.builder().exchangeFunction(exchangeFunction), BASE_URL);
    }
}
