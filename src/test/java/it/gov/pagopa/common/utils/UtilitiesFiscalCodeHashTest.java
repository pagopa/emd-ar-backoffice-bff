package it.gov.pagopa.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilitiesFiscalCodeHashTest {

    private static final String BASE = "/emd/backoffice/api/v1/citizen/";
    private static final String FISCAL_CODE = "RSSMRA85T10A562S";
    // SHA-256 of FISCAL_CODE computed independently of the production code
    private static final String HASH = "bd807ad78163541b86ec74dec91f028a83299ba3adc7e1345f8c213573cd22be";

    @Test
    void createSHA256ReturnsKnownHash() {
        assertThat(Utilities.createSHA256(FISCAL_CODE)).isEqualTo(HASH);
    }

    @Test
    void hashesFiscalCodeOnlyPath() {
        assertThat(Utilities.hashFiscalCodeInPath(BASE + FISCAL_CODE)).isEqualTo(BASE + HASH);
    }

    @Test
    void hashesFiscalCodeAndKeepsRemainingSegments() {
        assertThat(Utilities.hashFiscalCodeInPath(BASE + FISCAL_CODE + "/consents/tpp1"))
                .isEqualTo(BASE + HASH + "/consents/tpp1")
                .doesNotContain(FISCAL_CODE);
    }

    @Test
    void keepsStaticCitizenRoutes() {
        assertThat(Utilities.hashFiscalCodeInPath(BASE + "search")).isEqualTo(BASE + "search");
        assertThat(Utilities.hashFiscalCodeInPath(BASE + "consent/search")).isEqualTo(BASE + "consent/search");
    }

    @Test
    void keepsUnrelatedPathsAndNull() {
        assertThat(Utilities.hashFiscalCodeInPath("/emd/backoffice/api/v1/tpp/abc"))
                .isEqualTo("/emd/backoffice/api/v1/tpp/abc");
        assertThat(Utilities.hashFiscalCodeInPath(BASE)).isEqualTo(BASE);
        assertThat(Utilities.hashFiscalCodeInPath(null)).isNull();
    }
}
