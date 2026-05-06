package it.gov.pagopa.emd.ar.backoffice.connector.v1;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import reactor.core.publisher.Mono;

/**
 * Abstraction for retrieving Tpp data.</p>
 */
public interface TppConnectorV1 {

    /**
     * Saves TPP data by sending a POST request to the remote emd-tpp service.</p>
     * @param tppDTO the TPP data to save
     * @return {@code Mono<String>} containing the tppId of the saved TPP or an error if the operation fails
     */
    Mono<String> saveTpp(TppDTOV1 tppDTO);
}
