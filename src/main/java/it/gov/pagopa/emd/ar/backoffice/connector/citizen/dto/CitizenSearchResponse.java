package it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Cursor-paginated search response returned by Citizen. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CitizenSearchResponse {
    private List<CitizenSearchItem> content;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private String nextCursor;
    private boolean hasNext;
}

