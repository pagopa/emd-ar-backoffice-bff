package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
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
     * Looks up an existing TPP by its {@code entityId} (CF o P.IVA).
     *
     * @param entityId the fiscal code or VAT number
     * @return {@code Mono<TppIdResponseDTOV1>} with the tppId if found,
     *         or a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         (HTTP 404) if no TPP exists for that entityId
     */
    Mono<TppIdResponseDTOV1> getTppByEntityId(String entityId);

    /**
     * Deletes a TPP from the emd-tpp service and its associated Keycloak client.
     * <p><strong>NOTE:</strong> This operation is intended for testing purposes only
     * and must not be exposed on APIM.</p>
     *
     * @param tppId the identifier of the TPP to delete
     * @return {@code Mono<Void>} completing when both the TPP record and the Keycloak client
     *         have been removed
     */
    Mono<Void> deleteTppAndKeycloakClient(String tppId);

    /**
     * Retrieves the PagoPA credentials (Keycloak client ID and secret) associated with the
     * given {@code tppId}. No caching or persistence is performed.
     *
     * <p>Delegates directly to Keycloak — the emd-tpp microservice is not involved.</p>
     *
     * @param tppId the TPP identifier (equals the Keycloak {@code clientId})
     * @return {@code Mono<TppPagopaCredentialsDTOV1>} with the resolved credentials,
     *         or 404 if no Keycloak client exists for that {@code tppId}
     */
    Mono<TppPagopaCredentialsDTOV1> getTppPagopaCredentials(String tppId);
}
