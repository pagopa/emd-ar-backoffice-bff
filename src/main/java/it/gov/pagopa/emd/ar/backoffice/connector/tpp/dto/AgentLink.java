package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent deep-link as expected by the emd-tpp downstream service.
 * Unknown fields from the upstream are silently ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentLink {
    private String fallBackLink;
    private Map<String, VersionDetails> versions;
}

