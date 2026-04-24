package it.gov.pagopa.emd.ar.backoffice.dto.v1;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequestDTOV1 {
    @NotBlank(message = "Token must not be blank")
    private String token;
}
