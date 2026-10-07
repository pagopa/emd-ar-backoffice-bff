package it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Citizen search result item returned by the upstream service. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CitizenSearchItem {
    private String fiscalCode;
    private long consentCount;
}

