package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.citizen.CitizenConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenSearchItem;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.service.citizen.CitizenServiceImpl;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitizenSearchServiceImplTest {

    @Mock
    private CitizenConnector citizenConnector;

    private CitizenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CitizenServiceImpl(citizenConnector);
    }

    @Test
    void firstPageUsesUppercaseFiscalCodeAndDefaultSizeWithoutCursor() {
        when(citizenConnector.searchByFiscalCode(eq("RSSMRA85T10A562S"), isNull(), eq(10)))
                .thenReturn(Mono.just(response(List.of(item("RSSMRA85T10A562S", 2)), 10, 34, 4,
                        "RSSMRA85T10A562S", true)));

        StepVerifier.create(service.searchByFiscalCode("rssmra85t10a562s", null, null))
                .assertNext(result -> {
                    assertThat(result.getContent()).hasSize(1);
                    assertThat(result.getContent().getFirst().getFiscalCode()).isEqualTo("RSSMRA85T10A562S");
                    assertThat(result.getContent().getFirst().getConsentCount()).isEqualTo(2);
                    assertThat(result.getPageSize()).isEqualTo(10);
                    assertThat(result.getTotalElements()).isEqualTo(34);
                    assertThat(result.getTotalPages()).isEqualTo(4);
                    assertThat(result.getNextCursor()).isEqualTo("RSSMRA85T10A562S");
                    assertThat(result.isHasNext()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void subsequentPageNormalizesCursorAndPreservesCitizenMetadataAndEmptyContent() {
        when(citizenConnector.searchByFiscalCode(eq("ABC"), eq("ABC12345D67E890F"), eq(25)))
                .thenReturn(Mono.just(response(List.of(), 25, 40, 2, null, false)));

        StepVerifier.create(service.searchByFiscalCode("abc", "abc12345d67e890f", "25"))
                .assertNext(result -> {
                    assertThat(result.getContent()).isEmpty();
                    assertThat(result.getPageSize()).isEqualTo(25);
                    assertThat(result.getTotalElements()).isEqualTo(40);
                    assertThat(result.getTotalPages()).isEqualTo(2);
                    assertThat(result.getNextCursor()).isNull();
                    assertThat(result.isHasNext()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void invalidFiscalCodesAreRejected() {
        for (String fiscalCode : new String[]{null, "AB", "A12345678901234567", "AB-123", "RSS11111L04L741XY"}) {
            StepVerifier.create(service.searchByFiscalCode(fiscalCode, null, null))
                    .expectError(ValidationException.class)
                    .verify();
        }
    }

    @Test
    void malformedCompleteFiscalCodeIsRejected() {
        StepVerifier.create(service.searchByFiscalCode("ABCDEF1234567890", null, null))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void invalidCursorIsRejected() {
        for (String cursor : new String[]{"ABC", "ABC12345D67E890!", "XYZ12345D67E890F"}) {
            StepVerifier.create(service.searchByFiscalCode("ABC", cursor, null))
                    .expectError(ValidationException.class)
                    .verify();
        }
    }

    @Test
    void invalidSizesAreRejected() {
        for (String size : new String[]{"0", "101", "not-a-number"}) {
            StepVerifier.create(service.searchByFiscalCode("ABC", null, size))
                    .expectError(ValidationException.class)
                    .verify();
        }
    }

    @Test
    void connectorErrorsArePropagated() {
        ExternalServiceException error = new ExternalServiceException("CITIZEN_SERVICE", "search", "failed");
        when(citizenConnector.searchByFiscalCode(eq("ABC"), isNull(), eq(10))).thenReturn(Mono.error(error));

        StepVerifier.create(service.searchByFiscalCode("ABC", null, null))
                .expectErrorSatisfies(actual -> assertThat(actual).isSameAs(error))
                .verify();

        verify(citizenConnector).searchByFiscalCode("ABC", null, 10);
    }

    private static CitizenSearchItem item(String fiscalCode, long consentCount) {
        return CitizenSearchItem.builder().fiscalCode(fiscalCode).consentCount(consentCount).build();
    }

    private static CitizenSearchResponse response(List<CitizenSearchItem> content, int pageSize,
                                                   long totalElements, int totalPages,
                                                   String nextCursor, boolean hasNext) {
        return CitizenSearchResponse.builder()
                .content(content)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}

