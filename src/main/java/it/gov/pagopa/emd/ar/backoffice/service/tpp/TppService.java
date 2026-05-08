package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import reactor.core.publisher.Mono;

public interface TppService {

    /**
     * Creates a new TPP by first saving it through the connector and then creating a
     * corresponding Keycloak client.
     *
     * @param tppDTO the TPP data to create
     * @return {@code Mono<String>} containing the tppId of the created TPP, or an error
     */
    Mono<String> createTppAndKeycloakClient(TppDTOV1 tppDTO);

    /**
     * Looks up an existing TPP by its {@code entityId} (CF or P.IVA).
     *
     * @param entityId the fiscal code or VAT number
     * @return {@code Mono<TppIdResponseDTOV1>} with the tppId if found,
     *         or a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         (HTTP 404) if no TPP exists for that entityId
     */
    Mono<TppIdResponseDTOV1> getTppByEntityId(String entityId);
}
