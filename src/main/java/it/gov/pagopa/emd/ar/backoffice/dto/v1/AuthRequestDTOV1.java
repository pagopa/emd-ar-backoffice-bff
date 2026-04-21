package it.gov.pagopa.emd.ar.backoffice.dto.v1;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequestDTOV1 {
    private String token;
}