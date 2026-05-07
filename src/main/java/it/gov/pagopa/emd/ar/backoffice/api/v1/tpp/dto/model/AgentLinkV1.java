package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Represents the Agent Link field with fallback link and version details.
 */
@Data
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public class AgentLinkV1 {
    private String fallBackLink;
    private Map<String, VersionDetailsV1> versions;
}

