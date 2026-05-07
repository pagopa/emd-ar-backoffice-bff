package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent deep-link as expected by the emd-tpp downstream service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLink {
    private String fallBackLink;
    private Map<String, VersionDetails> versions;
}

