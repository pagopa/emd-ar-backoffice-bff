package it.gov.pagopa.emd.ar.backoffice.model.v1;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a token section. It serves as a data model for storing token section information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TokenSectionV1 {
    private String contentType;
    private Map<String, String> pathAdditionalProperties;
    private Map<String, String> bodyAdditionalProperties;
}
