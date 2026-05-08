package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import it.gov.pagopa.emd.ar.backoffice.domain.model.User;
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
    private User userInfo;
    private String token;
}
