package it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** Deleted consent aggregate snapshot, keyed by TPP ID. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CitizenConsentSnapshotDTOV1 {

    private String fiscalCode;
    private Map<String, ConsentSnapshotDTOV1> consents;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConsentSnapshotDTOV1 {
        private Boolean tppState;
        private LocalDateTime tcDate;
    }
}
