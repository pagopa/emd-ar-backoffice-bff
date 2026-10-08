package it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** Citizen's enriched consent response, retaining consents as a TPP-ID-keyed JSON object. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CitizenConsentSearchResponse {

    private String fiscalCode;
    private Map<String, EnrichedConsent> consents;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnrichedConsent {
        private Boolean tppState;
        private LocalDateTime tcDate;
        private String entityId;
        private String businessName;
    }
}
