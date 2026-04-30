package it.gov.pagopa.emd.ar.backoffice.connector.v1;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import reactor.core.publisher.Mono;

/**
 * Abstraction for retrieving Tpp data.</p>
 */
public interface TppConnectorV1 {
    Mono<String> saveTpp(TppDTOV1 tppDTO);
}
