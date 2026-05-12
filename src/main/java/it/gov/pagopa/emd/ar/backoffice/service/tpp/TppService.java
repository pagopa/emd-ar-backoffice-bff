package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1;
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
     *
     * <p>Execution order:
     * <ol>
     *   <li>Resolves the {@code tppId} from the emd-tpp service via {@code entityId}.</li>
     *   <li>Deletes the Keycloak OIDC client using the resolved {@code tppId}.</li>
     *   <li>Deletes the TPP record from emd-tpp using the resolved {@code tppId}.</li>
     * </ol>
     * </p>
     *
     * <p><strong>NOTE:</strong> This operation is intended for testing purposes only
     * and must not be exposed on APIM.</p>
     *
     * @param entityId the fiscal code (CF) or VAT number (P.IVA) of the TPP to delete
     * @return {@code Mono<Void>} completing when both the TPP record and the Keycloak client
     *         have been removed
     */
    Mono<Void> deleteTppAndKeycloakClient(String entityId);

    /**
     * Retrieves the PagoPA credentials (Keycloak client ID and secret) for the TPP
     * identified by {@code entityId} (CF o P.IVA).
     *
     * <p>Execution order:
     * <ol>
     *   <li>Resolves the {@code tppId} from the emd-tpp service via {@code entityId}.</li>
     *   <li>Fetches the Keycloak client credentials using the resolved {@code tppId}.</li>
     * </ol>
     * No caching or persistence is performed.</p>
     *
     * @param entityId the fiscal code (CF) or VAT number (P.IVA) of the TPP
     * @return {@code Mono<TppPagopaCredentialsDTOV1>} with the resolved credentials,
     *         or 404 if no TPP or Keycloak client exists for that {@code entityId}
     */
    Mono<TppPagopaCredentialsDTOV1> getTppPagopaCredentials(String entityId);

    /**
     * Retrieves the token-section credentials stored in the database for the TPP
     * identified by {@code entityId} (CF o P.IVA).
     *
     * <p>Execution order:
     * <ol>
     *   <li>Resolves the {@code tppId} from the emd-tpp service via {@code entityId}.</li>
     *   <li>Fetches the token section from emd-tpp using the resolved {@code tppId}
     *       ({@code GET /emd/tpp/{tppId}/token}).</li>
     * </ol>
     * No caching or intermediate storage is performed.</p>
     *
     * @param entityId the fiscal code (CF) or VAT number (P.IVA) of the TPP
     * @return {@code Mono<TokenSectionDTOV1>} with the token configuration,
     *         or 404 if no TPP exists for that {@code entityId}
     */
    Mono<TokenSectionDTOV1> getTppCredentials(String entityId);
}
