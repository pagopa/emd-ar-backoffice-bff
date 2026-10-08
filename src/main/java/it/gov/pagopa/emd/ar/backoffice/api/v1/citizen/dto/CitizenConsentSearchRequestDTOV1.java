package it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto;

import it.gov.pagopa.emd.ar.backoffice.domain.validation.FiscalCodeValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for searching all enriched consents of one citizen. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CitizenConsentSearchRequestDTOV1 {

    @NotBlank(message = "fiscalCode must not be blank")
    @Pattern(regexp = FiscalCodeValidation.COMPLETE_FISCAL_CODE_REGEX,
            message = "fiscalCode format is invalid")
    private String fiscalCode;
}
