package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TppTemplateValidationRequestDTOV1 {
    @NotBlank
    private String templateSource;
}
