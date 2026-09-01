package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Data Transfer Object for updating the payment authorization of a TPP.
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class TppUpdateIsPaymentEnabledDTOV1 {
    @NotNull
    private Boolean isPaymentEnabled;
}