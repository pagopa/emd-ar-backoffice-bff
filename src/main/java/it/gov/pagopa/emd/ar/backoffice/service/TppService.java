package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import reactor.core.publisher.Mono;

public interface TppService {
    
    /**
     * <p>Creates a new TPP by first saving it through the connector and then creating a corresponding Keycloak client.</p>
     * @param tppDTO the TPP data to create
     * @return {@code Mono<String>} containing the tppId of the created TPP or an error if the operation fails
     */
    Mono<String> createTppAndKeycloakClient(TppDTOV1 tppDTO);

    /**
     * <p>Retrieves the tppId of a TPP by its entityId.</p>
     * @param entityId the entityId of the TPP to retrieve
     * @return {@code Mono<String>} containing the tppId of the TPP or an error if the operation fails
     */
    Mono<String> getTppIdByEntityId(String entityId);
}
