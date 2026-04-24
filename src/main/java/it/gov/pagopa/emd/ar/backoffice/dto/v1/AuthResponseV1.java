package it.gov.pagopa.emd.ar.backoffice.dto.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponseV1 {
    private String status;
    private String message;
    private UserDTOV1 userInfo;
    private String token;
}
