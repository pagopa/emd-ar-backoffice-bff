package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal connector-layer representation of the response from the emd-tpp service
 * when querying a TPP by {@code entityId} ({@code GET /emd/tpp/entityId/{entityId}}).
 *
 * <p>The upstream service returns a richer payload ({@code TppDTOWithoutTokenSection}),
 * but the BFF only needs the {@code tppId} at this point. Unknown fields are ignored via
 * {@link JsonIgnoreProperties} so future additions to the upstream DTO do not break deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TppEntityIdResponse(String tppId) {}

