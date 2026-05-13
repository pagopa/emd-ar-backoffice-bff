package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contact information sent to the emd-tpp downstream service.
 * No Jakarta validation annotations — format constraints are enforced at the API layer.
 * Unknown fields from the upstream are silently ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Contact {
    private String name;
    private String number;
    private String email;
}

