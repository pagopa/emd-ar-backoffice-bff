package it.gov.pagopa.emd.ar.backoffice.service.citizen;

import it.gov.pagopa.emd.ar.backoffice.connector.citizen.CitizenConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenConsentSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitizenConsentSaveServiceImplTest {

    @Mock
    private CitizenConnector citizenConnector;

    private CitizenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CitizenServiceImpl(citizenConnector);
    }

    @Test
    void saveNormalizesFiscalCodeAndReturnsOnlyRequestedTppConsent() {
        LocalDateTime tcDate = LocalDateTime.parse("2026-11-11T11:11:11.000");
        when(citizenConnector.saveCitizenConsent("RSSMRA85T10A562S", "TPP_TARGET"))
                .thenReturn(Mono.just(CitizenConsentSearchResponse.builder()
                        .fiscalCode("RSSMRA85T10A562S")
                        .consents(Map.of(
                                "TPP_TARGET", CitizenConsentSearchResponse.EnrichedConsent.builder()
                                        .tppState(true).tcDate(tcDate).build(),
                                "TPP_OTHER", CitizenConsentSearchResponse.EnrichedConsent.builder()
                                        .tppState(false).tcDate(tcDate).build()))
                        .build()));

        StepVerifier.create(service.saveCitizenConsent("rssmra85t10a562s", "TPP_TARGET"))
                .assertNext(response -> {
                    assertThat(response.getFiscalCode()).isEqualTo("RSSMRA85T10A562S");
                    assertThat(response.getConsents()).containsOnlyKeys("TPP_TARGET");
                    assertThat(response.getConsents().get("TPP_TARGET").getTppState()).isTrue();
                    assertThat(response.getConsents().get("TPP_TARGET").getTcDate()).isEqualTo(tcDate);
                })
                .verifyComplete();

        verify(citizenConnector).saveCitizenConsent("RSSMRA85T10A562S", "TPP_TARGET");
    }

    @Test
    void saveRejectsInvalidFiscalCodeOrBlankTppIdBeforeCallingConnector() {
        StepVerifier.create(service.saveCitizenConsent("not-a-fiscal-code", "TPP_TARGET"))
                .expectError(ValidationException.class)
                .verify();
        StepVerifier.create(service.saveCitizenConsent(null, "TPP_TARGET"))
                .expectError(ValidationException.class)
                .verify();
        StepVerifier.create(service.saveCitizenConsent("RSSMRA85T10A562S", " "))
                .expectError(ValidationException.class)
                .verify();
        StepVerifier.create(service.saveCitizenConsent("RSSMRA85T10A562S", null))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(citizenConnector);
    }

    @Test
    void savePropagatesConnectorFailure() {
        ExternalServiceException failure = new ExternalServiceException("CITIZEN_SERVICE", "save", "failed");
        when(citizenConnector.saveCitizenConsent("RSSMRA85T10A562S", "TPP_TARGET")).thenReturn(Mono.error(failure));

        StepVerifier.create(service.saveCitizenConsent("RSSMRA85T10A562S", "TPP_TARGET"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }
}
