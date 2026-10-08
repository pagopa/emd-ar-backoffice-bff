package it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request payload sent to Citizen's enriched-consent search endpoint. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CitizenConsentSearchRequest {
    private String fiscalCode;
}
