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
    private static final String FISCAL_CODE_PATH_VALUE = "FC /?#";
    private static final String TPP_ID_PATH_VALUE = "TPP /? value";
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
    void deletesAggregateUsingEncodedFiscalCodePathWithoutBodyAndReturnsSnapshot() {
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        CitizenConnectorImpl connector = connectorWith(BASE_URL + "/emd/citizen", request -> {
            capturedUrl.set(request.url().toString());
            capturedMethod.set(request.method());
            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(mockRequest, ExchangeStrategies.withDefaults())
                    .doOnSuccess(ignored -> capturedBody.set(mockRequest.getBodyAsString().block()))
                    .thenReturn(jsonResponse(HttpStatus.OK, """
                            {
                              "fiscalCode": "RSSMRA85T10A562S",
                              "consents": {
                                "TPP_XYZ_123": {
                                  "tppState": true,
                                  "tcDate": "2026-10-08T12:30:00"
                                }
                              }
                            }
                            """));
        });

        StepVerifier.create(connector.deleteCitizenConsents(FISCAL_CODE))
                .assertNext(response -> {
                    assertThat(response.getFiscalCode()).isEqualTo(FISCAL_CODE);
                    assertThat(response.getConsents()).containsOnlyKeys("TPP_XYZ_123");
                    var consent = response.getConsents().get("TPP_XYZ_123");
                    assertThat(consent.getTppState()).isTrue();
                    assertThat(consent.getTcDate()).isEqualTo(LocalDateTime.parse("2026-10-08T12:30:00"));
                })
                .verifyComplete();

        assertThat(capturedMethod.get()).isEqualTo(HttpMethod.DELETE);
        assertThat(capturedUrl.get()).isEqualTo(BASE_URL + "/emd/citizen/" + FISCAL_CODE);
        assertThat(capturedBody.get()).isEmpty();
    }

    @Test
    void encodesFiscalCodeAsASinglePathSegment() {
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        CitizenConnectorImpl connector = connectorWith(request -> {
            capturedUrl.set(request.url().toASCIIString());
            return Mono.just(jsonResponse(HttpStatus.OK, "{\"fiscalCode\":\"FC/with space\",\"consents\":{}}"));
        });

        StepVerifier.create(connector.deleteCitizenConsents("FC/with space"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(capturedUrl.get()).isEqualTo(BASE_URL + "/emd/citizen/FC%2Fwith%20space");
    }

    @Test
    void deleteMaps404ByStatusWithoutUsingUpstreamMessage() {
        CitizenConnectorImpl connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.NOT_FOUND,
                "{\"code\":\"CITIZEN_NOT_ONBOARDED\",\"message\":\"private upstream detail\"}")));

        StepVerifier.create(connector.deleteCitizenConsents(FISCAL_CODE))
                .expectErrorMatches(error -> error instanceof ResourceNotFoundException
                        && !error.getMessage().contains("private upstream detail"))
                .verify();
    }

    @Test
    void deleteMapsOtherUpstreamAndNetworkErrorsToExternalServiceException() {
        CitizenConnectorImpl upstreamFailure = connectorWith(request -> Mono.just(jsonResponse(
                HttpStatus.BAD_GATEWAY, "{\"message\":\"private upstream detail\"}")));
        StepVerifier.create(upstreamFailure.deleteCitizenConsents(FISCAL_CODE))
                .expectErrorMatches(error -> error instanceof ExternalServiceException
                        && !error.getMessage().contains("private upstream detail"))
                .verify();

        CitizenConnectorImpl networkFailure = connectorWith(request -> Mono.error(new IllegalStateException("private network detail")));
        StepVerifier.create(networkFailure.deleteCitizenConsents(FISCAL_CODE))
                .expectError(ExternalServiceException.class)
                .verify();
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
    void putToggleEncodesBothPathSegmentsAndSendsNoBody() {
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        CitizenConnectorImpl connector = connectorWith(request -> {
            capturedUrl.set(request.url().toString());
            capturedMethod.set(request.method());
            MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(mockRequest, ExchangeStrategies.withDefaults())
                    .doOnSuccess(ignored -> capturedBody.set(mockRequest.getBodyAsString().block()))
                    .thenReturn(jsonResponse(HttpStatus.OK,
                            "{\"fiscalCode\":\"FC /?#\",\"consents\":{\"TPP /? value\":{"
                                    + "\"tppState\":false,\"tcDate\":\"2025-10-17T13:18:37.313\"}}}"));
        });

        StepVerifier.create(connector.toggleCitizenConsent(FISCAL_CODE_PATH_VALUE, TPP_ID_PATH_VALUE))
                .assertNext(response -> {
                    assertThat(response.getFiscalCode()).isEqualTo(FISCAL_CODE_PATH_VALUE);
                    assertThat(response.getConsents()).containsOnlyKeys(TPP_ID_PATH_VALUE);
                    assertThat(response.getConsents().get(TPP_ID_PATH_VALUE).getTppState()).isFalse();
                    assertThat(response.getConsents().get(TPP_ID_PATH_VALUE).getTcDate())
                            .isEqualTo(LocalDateTime.parse("2025-10-17T13:18:37.313"));
                })
                .verifyComplete();

        assertThat(capturedMethod.get()).isEqualTo(HttpMethod.PUT);
        assertThat(capturedUrl.get()).isEqualTo(BASE_URL + "/emd/citizen/FC%20%2F%3F%23/TPP%20%2F%3F%20value");
        assertThat(capturedBody.get()).isNullOrEmpty();
    }

    @Test
    void toggleResponseDeserializesEnabledConsent() {
        CitizenConnectorImpl connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.OK,
                "{\"fiscalCode\":\"" + FISCAL_CODE + "\",\"consents\":{\"TPP_XYZ_123\":{"
                        + "\"tppState\":true,\"tcDate\":\"2025-10-17T13:18:37.313\"}}}")));

        StepVerifier.create(connector.toggleCitizenConsent(FISCAL_CODE, "TPP_XYZ_123"))
                .assertNext(response -> assertThat(response.getConsents().get("TPP_XYZ_123").getTppState()).isTrue())
                .verifyComplete();
    }

    @Test
    void toggleNotFoundMapsToBffResourceNotFound() {
        CitizenConnectorImpl connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.NOT_FOUND,
                "{\"code\":\"NOT_FOUND\",\"message\":\"private detail\"}")));

        StepVerifier.create(connector.toggleCitizenConsent(FISCAL_CODE, "TPP_XYZ_123"))
                .expectErrorMatches(error -> error instanceof ResourceNotFoundException
                        && !error.getMessage().contains("private detail"))
                .verify();
    }

    @Test
    void toggleUpstreamServerErrorMapsToSanitizedExternalServiceExceptionWithoutRetry() {
        AtomicReference<Integer> requestCount = new AtomicReference<>(0);
        CitizenConnectorImpl connector = connectorWith(request -> {
            requestCount.updateAndGet(count -> count + 1);
            return Mono.just(jsonResponse(HttpStatus.SERVICE_UNAVAILABLE,
                    "{\"code\":\"GENERIC_ERROR\",\"message\":\"private detail\"}"));
        });

        StepVerifier.create(connector.toggleCitizenConsent(FISCAL_CODE, "TPP_XYZ_123"))
                .expectErrorMatches(error -> error instanceof ExternalServiceException
                        && !error.getMessage().contains("private detail"))
                .verify();

        assertThat(requestCount.get()).isEqualTo(1);
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
        return connectorWith(BASE_URL, exchangeFunction);
    }

    private static CitizenConnectorImpl connectorWith(String baseUrl, ExchangeFunction exchangeFunction) {
        return new CitizenConnectorImpl(WebClient.builder().exchangeFunction(exchangeFunction), baseUrl);
    }
}
