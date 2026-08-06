package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Connector-layer DTO for the paginated response from {@code GET /emd/tpp/search}.
 *
 * <p>Maps the upstream {@code TppSearchResponseDTO} payload. Each element in
 * {@code content} is a {@link TppEntityIdResponse} (i.e. {@code TppDTOWithoutTokenSection}
 * — no token-section credentials). Unknown fields are silently ignored.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TppSearchResponse {

    /** List of TPPs matching the search criteria (no token section). */
    private List<TppEntityIdResponse> content;

    /** Zero-based index of the current page (normalised by the upstream service). */
    private int page;

    /** Effective page size (after default/cap applied by the upstream service). */
    private int size;

    /** Total number of elements satisfying the search criteria. */
    private long totalElements;

    /** Total number of pages. */
    private int totalPages;
}

