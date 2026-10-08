package it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Cursor-paginated fiscal-code search response exposed by the BFF. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CitizenSearchResponseDTOV1 {
    private List<CitizenSearchItemDTOV1> content;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private String nextCursor;
    private boolean hasNext;
}

