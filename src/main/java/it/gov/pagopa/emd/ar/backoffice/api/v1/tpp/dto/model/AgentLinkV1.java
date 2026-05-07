package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model;

import java.util.HashMap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents the Agent Link field with fallback link and version details.
 */
@Data
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public class AgentLinkV1 {
    private String fallBackLink;
    private HashMap<String, VersionDetailsV1> versions;
}
