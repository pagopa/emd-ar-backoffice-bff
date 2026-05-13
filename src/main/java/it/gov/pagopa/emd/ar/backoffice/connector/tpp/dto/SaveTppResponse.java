package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the minimal response payload returned by the remote emd-tpp service
 * after a successful TPP save operation.
 * <p>
 * Using a dedicated connector model avoids coupling the API layer DTO ({@code TppDTOV1})
 * to the upstream service contract. Unknown fields from the upstream are silently ignored.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveTppResponse(String tppId) {}
