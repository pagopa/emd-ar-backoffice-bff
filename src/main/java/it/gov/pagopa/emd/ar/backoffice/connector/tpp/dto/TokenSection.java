package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Token section configuration as expected by the emd-tpp downstream service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenSection {
    private String contentType;
    private Map<String, String> pathAdditionalProperties;
    private Map<String, String> bodyAdditionalProperties;
}

