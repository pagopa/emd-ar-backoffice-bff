package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequestDTOV1 {
    private String refreshToken;
}
