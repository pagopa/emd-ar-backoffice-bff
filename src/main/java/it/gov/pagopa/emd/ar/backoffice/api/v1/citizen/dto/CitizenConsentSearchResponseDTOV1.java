package it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** Enriched citizen consents, keyed by TPP ID. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CitizenConsentSearchResponseDTOV1 {

    private String fiscalCode;
    private Map<String, ConsentDTOV1> consents;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConsentDTOV1 {
        private Boolean tppState;
        private LocalDateTime tcDate;
        private String entityId;
        private String businessName;
    }
}
