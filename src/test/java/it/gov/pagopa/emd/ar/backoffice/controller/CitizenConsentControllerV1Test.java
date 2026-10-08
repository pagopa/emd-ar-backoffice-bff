package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.handler.ControllerExceptionHandler;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.controller.CitizenControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenConsentSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import jakarta.validation.ValidationException;
import it.gov.pagopa.emd.ar.backoffice.service.citizen.CitizenService;
import it.gov.pagopa.common.utils.Utilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CitizenConsentControllerV1Test {

    private static final String URL = "/emd/backoffice/api/v1/citizen/consent/search";
    private static final String TOGGLE_URL = "/emd/backoffice/api/v1/citizen/{fiscalCode}/consents/{tppId}";
    private static final String FISCAL_CODE = "RSSMRA85T10A562S";

    private CitizenService citizenService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        citizenService = Mockito.mock(CitizenService.class);
        Utilities utilities = Mockito.mock(Utilities.class);
        Mockito.lenient().when(utilities.getTraceId()).thenReturn("test-trace-id");
        webTestClient = WebTestClient.bindToController(new CitizenControllerImplV1(citizenService))
                .controllerAdvice(new ControllerExceptionHandler(utilities))
                .build();
    }

    @Test
    void returnsExactEnrichedConsentJsonAndForwardsBodyCodeToService() {
        LocalDateTime tcDate = LocalDateTime.parse("2025-10-17T13:18:37.313");
        Map<String, CitizenConsentSearchResponseDTOV1.ConsentDTOV1> consents = new LinkedHashMap<>();
        consents.put("TPP_XYZ_123", CitizenConsentSearchResponseDTOV1.ConsentDTOV1.builder()
                .tppState(true)
                .tcDate(tcDate)
                .entityId("entity-123")
                .businessName("Nome Azienda TPP")
                .build());
        when(citizenService.searchCitizenConsents(eq(FISCAL_CODE))).thenReturn(Mono.just(
                CitizenConsentSearchResponseDTOV1.builder().fiscalCode(FISCAL_CODE).consents(consents).build()));

        webTestClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"fiscalCode":"RSSMRA85T10A562S"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.fiscalCode").isEqualTo(FISCAL_CODE)
                .jsonPath("$.consents").isMap()
                .jsonPath("$.consents.TPP_XYZ_123.tppState").isEqualTo(true)
                .jsonPath("$.consents.TPP_XYZ_123.tcDate").isEqualTo("2025-10-17T13:18:37.313")
                .jsonPath("$.consents.TPP_XYZ_123.entityId").isEqualTo("entity-123")
                .jsonPath("$.consents.TPP_XYZ_123.businessName").isEqualTo("Nome Azienda TPP");

        verify(citizenService).searchCitizenConsents(FISCAL_CODE);
    }

    @Test
    void keepsAnEmptyConsentMapAsAnObject() {
        when(citizenService.searchCitizenConsents(eq(FISCAL_CODE))).thenReturn(Mono.just(
                CitizenConsentSearchResponseDTOV1.builder().fiscalCode(FISCAL_CODE).consents(Map.of()).build()));

        webTestClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fiscalCode\":\"RSSMRA85T10A562S\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.consents").isMap()
                .jsonPath("$.consents").isEmpty();
    }

    @Test
    void missingBlankAndMalformedFiscalCodesReturnBffBadRequestWithoutCallingService() {
        for (String body : new String[]{"{}", "{\"fiscalCode\":\"\"}", "{\"fiscalCode\":\"   \"}",
                "{\"fiscalCode\":\"ABCDEF1234567890\"}"}) {
            webTestClient.post()
                    .uri(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                    .jsonPath("$.message").exists();
        }
        verifyNoInteractions(citizenService);
    }

    @Test
    void mapsCitizenNotFoundToBff404ErrorDto() {
        when(citizenService.searchCitizenConsents(eq(FISCAL_CODE)))
                .thenReturn(Mono.error(new ResourceNotFoundException("Citizen", "consent")));

        webTestClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fiscalCode\":\"RSSMRA85T10A562S\"}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").value(String.class, message -> org.assertj.core.api.Assertions.assertThat(message)
                        .doesNotContain(FISCAL_CODE));
    }

    @Test
    void mapsUpstreamValidationFailureToBff400ErrorDto() {
        when(citizenService.searchCitizenConsents(eq(FISCAL_CODE)))
                .thenReturn(Mono.error(new ValidationException("Citizen rejected the consent search request.")));

        webTestClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fiscalCode\":\"RSSMRA85T10A562S\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(String.class, message -> org.assertj.core.api.Assertions.assertThat(message)
                        .doesNotContain(FISCAL_CODE));
    }

    @Test
    void mapsUpstreamFailureToSanitizedBffGatewayErrorDto() {
        when(citizenService.searchCitizenConsents(eq(FISCAL_CODE)))
                .thenReturn(Mono.error(new ExternalServiceException("CITIZEN_SERVICE", "search", "private upstream detail")));

        webTestClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fiscalCode\":\"RSSMRA85T10A562S\"}")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.code").isEqualTo("GENERIC_ERROR")
                .jsonPath("$.message").value(String.class, message -> org.assertj.core.api.Assertions.assertThat(message)
                        .doesNotContain("private upstream detail", FISCAL_CODE));
    }

    @Test
    void togglesConsentWithoutRequestBodyAndReturnsBothStateValues() {
        LocalDateTime tcDate = LocalDateTime.parse("2025-10-17T13:18:37.313");
        for (boolean tppState : new boolean[]{true, false}) {
            Map<String, CitizenConsentSearchResponseDTOV1.ConsentDTOV1> consents = Map.of(
                    "TPP_XYZ_123", CitizenConsentSearchResponseDTOV1.ConsentDTOV1.builder()
                            .tppState(tppState).tcDate(tcDate).build());
            when(citizenService.toggleCitizenConsent(eq(FISCAL_CODE), eq("TPP_XYZ_123"))).thenReturn(Mono.just(
                    CitizenConsentSearchResponseDTOV1.builder().fiscalCode(FISCAL_CODE).consents(consents).build()));

            webTestClient.put()
                    .uri(TOGGLE_URL, FISCAL_CODE, "TPP_XYZ_123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.fiscalCode").isEqualTo(FISCAL_CODE)
                    .jsonPath("$.consents.TPP_XYZ_123.tppState").isEqualTo(tppState)
                    .jsonPath("$.consents.TPP_XYZ_123.tcDate").isEqualTo("2025-10-17T13:18:37.313");
        }

        verify(citizenService, Mockito.times(2)).toggleCitizenConsent(FISCAL_CODE, "TPP_XYZ_123");
    }

    @Test
    void mapsMissingCitizenOrConsentToBff404ErrorDto() {
        when(citizenService.toggleCitizenConsent(eq(FISCAL_CODE), eq("TPP_XYZ_123")))
                .thenReturn(Mono.error(new ResourceNotFoundException("Citizen consent", "requested TPP")));

        webTestClient.put()
                .uri(TOGGLE_URL, FISCAL_CODE, "TPP_XYZ_123")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").exists();
    }

    @Test
    void mapsToggleUpstreamFailureToSanitizedBffGatewayErrorDto() {
        when(citizenService.toggleCitizenConsent(eq(FISCAL_CODE), eq("TPP_XYZ_123")))
                .thenReturn(Mono.error(new ExternalServiceException(
                        "CITIZEN_SERVICE", "toggleCitizenConsent", "private upstream detail")));

        webTestClient.put()
                .uri(TOGGLE_URL, FISCAL_CODE, "TPP_XYZ_123")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.code").isEqualTo("GENERIC_ERROR")
                .jsonPath("$.message").value(String.class, message -> org.assertj.core.api.Assertions.assertThat(message)
                        .doesNotContain("private upstream detail", FISCAL_CODE));
    }
}
