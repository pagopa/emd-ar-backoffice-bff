package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Represents the Agent Link field with fallback link and version details.
 * <p>
 * The {@code versions} map keys are persisted as MongoDB/Cosmos DB field names, which cannot
 * contain a dot ({@code .}). A dot in a version key is therefore rejected as a client error
 * ({@code 400}) at request binding time via the {@link Pattern} constraint on the map key, instead
 * of failing later as an internal error during the database write.
 */
@Data
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public class AgentLinkV1 {
    private String fallBackLink;
    @Valid
    private Map<@Pattern(regexp = "^[^.]*$", message = "Agent link version keys must not contain a dot ('.')") String, VersionDetailsV1> versions;
}

