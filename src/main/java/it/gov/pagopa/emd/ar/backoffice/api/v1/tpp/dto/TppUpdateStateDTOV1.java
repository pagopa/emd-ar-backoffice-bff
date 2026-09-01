package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Data Transfer Object for updating the operational state of a TPP.
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class TppUpdateStateDTOV1 {
    private String tppId;
    @NotNull
    private Boolean state;
}
