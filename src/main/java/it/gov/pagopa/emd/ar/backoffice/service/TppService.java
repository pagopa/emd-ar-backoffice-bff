package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import reactor.core.publisher.Mono;

public interface TppService {
    
    /**
     * This method will save the provided TPP information, create a new client in Keycloak with the
     * same TPP information and return the tppId of the saved TPP as response.
     * @param tppDTO
     * @return
     */
    Mono<String> createTppAndKeycloakClient(TppDTOV1 tppDTO);
}
