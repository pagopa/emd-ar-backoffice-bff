package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TppConnectionResponseDTOV1 {
    /**
     * High-level result of the test.
     */
    private String status;

    /**
     * Categorization of the error (populated only if status is not SUCCESS).
     */
    private String errorType;

    /**
     * HTTP status code returned by the external TPP server (e.g., 200, 404, 500).
     */
    private Integer httpStatus;

    /**
     * Human-readable description of the result or error.
     */
    private String description;
}
