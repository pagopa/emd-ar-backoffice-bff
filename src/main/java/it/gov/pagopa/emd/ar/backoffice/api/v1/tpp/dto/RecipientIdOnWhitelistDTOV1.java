package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipientIdOnWhitelistDTOV1 {

    @NotBlank(message = "recipientId must not be blank")
    private String recipientId;
}
