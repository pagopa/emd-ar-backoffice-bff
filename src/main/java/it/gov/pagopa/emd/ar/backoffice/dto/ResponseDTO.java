package it.gov.pagopa.emd.ar.backoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResponseDTO {
    private String status;
    private String message;
    private String token;
}
