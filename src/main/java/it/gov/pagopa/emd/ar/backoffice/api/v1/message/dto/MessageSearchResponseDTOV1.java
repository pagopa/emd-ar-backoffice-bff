package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API-layer V1 paginated response for {@code GET /emd/backoffice/api/v1/message-core/search}.
 *
 * <p>Wraps a page of {@link MessageDTOV1} elements together with
 * standard pagination metadata (page index, page size, total elements and total
 * pages) reflecting the values normalised/capped by the upstream emd-message-core service.</p>
 */
@Data 
@Builder 
@AllArgsConstructor 
@NoArgsConstructor 
public class MessageSearchResponseDTOV1 {

    /** Current page of Messages. */
    private List<MessageDTOV1> content;

    /** Zero-based page index (normalised by the upstream service). */
    private int page;

    /** Effective page size (after default / cap applied upstream). */
    private int size;

    /** Total number of Messages satisfying the search criteria. */
    private long totalElements;

    /** Total number of pages. */
    private int totalPages;
}
