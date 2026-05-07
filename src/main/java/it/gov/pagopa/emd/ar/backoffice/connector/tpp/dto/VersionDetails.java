package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Link details for a specific version of an agent deep link,
 * as expected by the emd-tpp downstream service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDetails {
    private String link;
}

