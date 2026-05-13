package it.gov.pagopa.emd.ar.backoffice.connector.tpp;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppPatchRequest;
import reactor.core.publisher.Mono;

/**
 * Abstraction for the outbound TPP persistence adapter.
 */
public interface TppConnector {

    /**
     * Saves TPP data by sending a POST request to the remote emd-tpp service.
     *
     * @param request the outbound payload (already enriched with defaults)
     * @return {@code Mono<TppEntityIdResponse>} containing the full TPP representation returned by
     *         the upstream service, or an error if the operation fails
     */
    Mono<TppEntityIdResponse> saveTpp(TppCreateRequest request);

    /**
     * Deletes a previously saved TPP by its identifier.
     *
     * <p>Used as a <em>compensating transaction</em>: if the Keycloak client creation
     * fails after the TPP has been persisted, this method rolls back the DB record to
     * prevent orphan entries with {@code state=false}.
     *
     * @param tppId the identifier of the TPP to delete
     * @return {@code Mono<Void>} completing when the TPP is deleted, or an error
     */
    Mono<Void> deleteTpp(String tppId);

    /**
     * Retrieves a TPP by its {@code entityId} (CF or P.IVA).
     *
     * @param entityId the fiscal code or VAT number of the TPP
     * @return {@code Mono<TppEntityIdResponse>} containing at least the {@code tppId}, or
     *         a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         if the TPP does not exist (upstream 404)
     */
    Mono<TppEntityIdResponse> getTppByEntityId(String entityId);

    /**
     * Retrieves the token-section credentials for the TPP identified by {@code tppId},
     * calling {@code GET /emd/tpp/{tppId}/token} on the emd-tpp service.
     *
     * @param tppId the identifier of the TPP
     * @return {@code Mono<TokenSection>} with the token configuration, or a
     *         {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         if no TPP exists for that {@code tppId} (upstream 404)
     */
    Mono<TokenSection> getTppToken(String tppId);

    /**
     * Updates the token-section credentials for the TPP identified by {@code tppId},
     * calling {@code PUT /update/{tppId}/token} on the emd-tpp service.
     *
     * <p><strong>Privacy:</strong> the {@code tokenSection} body may contain sensitive values
     * (e.g. {@code client_secret}). Callers must never log the payload contents.</p>
     *
     * @param tppId       the identifier of the TPP to update
     * @param tokenSection the new token-section data to persist
     * @return {@code Mono<TokenSection>} with the persisted token section (mirrors the request body)
     *         on success, or an error if the upstream call fails
     *         (404 → {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException},
     *         others → {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException})
     */
    Mono<TokenSection> updateTppToken(String tppId, TokenSection tokenSection);

    /**
     * Partially updates the TPP identified by {@code tppId} by sending a
     * {@code PATCH /emd/tpp/{tppId}} request to the emd-tpp service.
     *
     * <p>Only the non-null fields in {@code patchRequest} are serialized and applied;
     * all omitted fields retain their existing values in the database.</p>
     *
     * @param tppId        the identifier of the TPP to patch
     * @param patchRequest the partial update payload
     * @return {@code Mono<TppEntityIdResponse>} with the full, updated TPP representation,
     *         or a {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException}
     *         if no TPP exists for that {@code tppId} (upstream 404), or an
     *         {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException}
     *         for any other upstream error
     */
    Mono<TppEntityIdResponse> patchTpp(String tppId, TppPatchRequest patchRequest);
}
