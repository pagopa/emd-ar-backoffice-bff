package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API-layer V1 paginated response for {@code GET /emd/backoffice/api/v1/tpp/search}.
 *
 * <p>Wraps a page of {@link TppDTOWithoutTokenSectionV1} elements together with
 * standard pagination metadata (page index, page size, total elements and total
 * pages) reflecting the values normalised/capped by the upstream emd-tpp service.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppSearchResponseDTOV1 {

    /** Current page of TPPs (no token section, no whitelistRecipient). */
    private List<TppDTOWithoutTokenSectionV1> content;

    /** Zero-based page index (normalised by the upstream service). */
    private int page;

    /** Effective page size (after default / cap applied upstream). */
    private int size;

    /** Total number of TPPs satisfying the search criteria. */
    private long totalElements;

    /** Total number of pages. */
    private int totalPages;
}

