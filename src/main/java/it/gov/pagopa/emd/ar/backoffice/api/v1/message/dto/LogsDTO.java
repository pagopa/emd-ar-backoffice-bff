package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogsDTO {
    private String timestamp;
    private String message;
    private String level;
    private String traceId;
}
