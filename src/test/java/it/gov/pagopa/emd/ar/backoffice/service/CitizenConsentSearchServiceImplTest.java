package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.citizen.CitizenConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenConsentSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.service.citizen.CitizenServiceImpl;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitizenConsentSearchServiceImplTest {

    @Mock
    private CitizenConnector citizenConnector;

    private CitizenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CitizenServiceImpl(citizenConnector);
    }

    @Test
    void normalizesFiscalCodeAndMapsAllConsentFieldsWithoutChangingMapOrLocalDateTime() {
        LocalDateTime tcDate = LocalDateTime.parse("2025-10-17T13:18:37.313");
        CitizenConsentSearchResponse.EnrichedConsent consent = CitizenConsentSearchResponse.EnrichedConsent.builder()
                .tppState(true)
                .tcDate(tcDate)
                .entityId("entity-123")
                .businessName("Nome Azienda TPP")
                .build();
        when(citizenConnector.searchCitizenConsents("RSSMRA85T10A562S"))
                .thenReturn(Mono.just(CitizenConsentSearchResponse.builder()
                        .fiscalCode("RSSMRA85T10A562S")
                        .consents(Map.of("TPP_XYZ_123", consent))
                        .build()));

        StepVerifier.create(service.searchCitizenConsents("rssmra85t10a562s"))
                .assertNext(response -> {
                    assertThat(response.getFiscalCode()).isEqualTo("RSSMRA85T10A562S");
                    assertThat(response.getConsents()).containsOnlyKeys("TPP_XYZ_123");
                    var mapped = response.getConsents().get("TPP_XYZ_123");
                    assertThat(mapped.getTppState()).isTrue();
                    assertThat(mapped.getTcDate()).isEqualTo(tcDate);
                    assertThat(mapped.getEntityId()).isEqualTo("entity-123");
                    assertThat(mapped.getBusinessName()).isEqualTo("Nome Azienda TPP");
                })
                .verifyComplete();
        verify(citizenConnector).searchCitizenConsents("RSSMRA85T10A562S");
    }

    @Test
    void preservesEmptyConsentMap() {
        when(citizenConnector.searchCitizenConsents("RSSMRA85T10A562S"))
                .thenReturn(Mono.just(CitizenConsentSearchResponse.builder()
                        .fiscalCode("RSSMRA85T10A562S").consents(Map.of()).build()));

        StepVerifier.create(service.searchCitizenConsents("RSSMRA85T10A562S"))
                .assertNext(response -> assertThat(response.getConsents()).isEmpty())
                .verifyComplete();
    }

    @Test
    void propagatesConnectorFailure() {
        ExternalServiceException failure = new ExternalServiceException("CITIZEN_SERVICE", "search", "failed");
        when(citizenConnector.searchCitizenConsents("RSSMRA85T10A562S")).thenReturn(Mono.error(failure));

        StepVerifier.create(service.searchCitizenConsents("RSSMRA85T10A562S"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }
}
