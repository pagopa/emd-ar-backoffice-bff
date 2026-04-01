package it.gov.pagopa.emd.ar.backoffice;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResponseDTO {
    private String status;
    private String message;
    private String token;
}
