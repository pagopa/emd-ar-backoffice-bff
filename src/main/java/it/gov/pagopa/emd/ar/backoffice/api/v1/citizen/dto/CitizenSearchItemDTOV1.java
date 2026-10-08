package it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A fiscal-code result and its associated consent count. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CitizenSearchItemDTOV1 {
    private String fiscalCode;
    private long consentCount;
}

