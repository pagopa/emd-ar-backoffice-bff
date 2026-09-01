package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOWithoutTokenSectionV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPatchDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateIsPaymentEnabledDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateStateDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.RecipientIdOnWhitelistDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppConnectionResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.mapper.TppConnectorMapper;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakClientService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;

/**
 * Orchestrates TPP creation:
 * <ol>
 *   <li>Maps the API DTO to the connector request (applying defaults via {@link TppConnectorMapper}).</li>
 *   <li>Persists the TPP via the connector (obtains the {@code tppId}).</li>
 *   <li>Creates the corresponding Keycloak OIDC client using the resolved Keycloak Client ID.</li>
 * </ol>
 *
 * <p><strong>Keycloak Client ID resolution:</strong> the identifier used for Keycloak
 * operations is determined by {@link #resolveKeycloakClientId(TppEntityIdResponse)} with the
 * following priority:
 * <ol>
 *   <li>If the TPP response contains a non-empty {@code clientId}, that value is used
 *       (covers legacy/pre-existing TPPs whose Keycloak client was created with a random ID).</li>
 *   <li>Otherwise the TPP {@code entityId} is used (default for all new TPPs).</li>
 * </ol>
 * </p>
 *
 * <p><strong>Consistency / compensation:</strong> if Keycloak fails after the TPP is
 * persisted, the service attempts a <em>compensating delete</em> of the DB record so
 * that no orphan entry is left behind. If the compensation itself fails the error is
 * logged and the original Keycloak exception is propagated (worst case: a single inert
 * record with {@code state=false}, subject to manual or batch reconciliation).</p>
 *
 * <p>Keycloak client creation is idempotent: a 409 Conflict (client already exists)
 * is resolved by looking up the existing client, so a retry of the full flow after a
 * partial failure is always safe.</p>
 */
@Slf4j
@Service
public class TppServiceImpl implements TppService {

    private final TppConnector tppConnector;
    private final KeycloakClientService keycloakClientService;

    public TppServiceImpl(TppConnector tppConnector, KeycloakClientService keycloakClientService) {
        this.tppConnector = tppConnector;
        this.keycloakClientService = keycloakClientService;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppResponseDTOV1> createTppAndKeycloakClient(String entityId, TppDTOV1 tppDTO) {
        log.info("[AR-BFF][TPP_CREATE] Creating new TPP for entityId={}: {}", entityId, tppDTO.getBusinessName());
        return tppConnector.saveTpp(TppConnectorMapper.toCreateRequest(entityId, tppDTO))
                .flatMap(tppResponse -> {
                    String tppId = tppResponse.getTppId();
                    String keycloakClientId = resolveKeycloakClientId(tppResponse);
                    log.info("[AR-BFF][TPP_CREATE] TPP persisted with id={}. Creating Keycloak client with clientId={}.", tppId, keycloakClientId);
                    return keycloakClientService.createKeycloakClient(keycloakClientId, entityId, tppDTO.getBusinessName())
                            .onErrorResume(ex -> compensateDelete(tppId, ex))
                            .thenReturn(tppResponse);
                })
                .map(TppConnectorMapper::toTppResponseDTOV1)
                .doOnSuccess(r -> log.info("[AR-BFF][TPP_CREATE] TPP creation completed. tppId={}", r.getTppId()))
                .doOnError(e -> log.error("[AR-BFF][TPP_CREATE] Error during TPP creation: {}", e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppResponseDTOV1> getTppByEntityId(String entityId, boolean detailed) {
        log.info("[AR-BFF][TPP_GET] Looking up TPP by entityId={}, detailed={}", entityId, detailed);
        return tppConnector.getTppByEntityId(entityId)
                .map(r -> detailed
                        ? TppConnectorMapper.toTppResponseDTOV1Detailed(r)
                        : TppConnectorMapper.toTppResponseDTOV1(r))
                .doOnSuccess(r -> log.info("[AR-BFF][TPP_GET] Found TPP for entityId={}", entityId))
                .doOnError(e -> log.warn("[AR-BFF][TPP_GET] TPP not found for entityId={}: {}", entityId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> deleteTppAndKeycloakClient(String entityId) {
        log.info("[AR-BFF][TPP_DELETE] Deleting TPP and Keycloak client for entityId={}", entityId);
        return tppConnector.getTppByEntityId(entityId)
                .flatMap(response -> {
                    String tppId = response.getTppId();
                    String keycloakClientId = resolveKeycloakClientId(response);
                    log.info("[AR-BFF][TPP_DELETE] Resolved tppId={}, keycloakClientId={} for entityId={}", tppId, keycloakClientId, entityId);
                    return keycloakClientService.deleteKeycloakClient(keycloakClientId)
                            .then(Mono.defer(() -> tppConnector.deleteTpp(tppId)));
                })
                .doOnSuccess(v -> log.info("[AR-BFF][TPP_DELETE] TPP and Keycloak client deleted for entityId={}", entityId))
                .doOnError(e -> log.error("[AR-BFF][TPP_DELETE] Error during TPP deletion for entityId={}: {}", entityId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppPagopaCredentialsDTOV1> getTppPagopaCredentials(String entityId) {
        log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Retrieving PagoPA credentials for entityId={}", entityId);
        return tppConnector.getTppByEntityId(entityId)
                .flatMap(response -> {
                    String keycloakClientId = resolveKeycloakClientId(response);
                    log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Resolved tppId={}, keycloakClientId={} for entityId={}", response.getTppId(), keycloakClientId, entityId);
                    return keycloakClientService.getPagopaClientCredentials(keycloakClientId);
                })
                .doOnSuccess(c -> log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] PagoPA credentials retrieved for entityId={}", entityId))
                .doOnError(e -> log.error("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Failed to retrieve PagoPA credentials for entityId={}: {}", entityId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TokenSectionDTOV1> getTppCredentials(String entityId) {
        log.info("[AR-BFF][TPP_CREDENTIALS] Retrieving token-section credentials for entityId={}", entityId);
        return tppConnector.getTppByEntityId(entityId)
                .flatMap(response -> {
                    log.info("[AR-BFF][TPP_CREDENTIALS] Resolved tppId={} for entityId={}", response.getTppId(), entityId);
                    return tppConnector.getTppToken(response.getTppId());
                })
                .map(tokenSection -> new TokenSectionDTOV1(
                        tokenSection.getContentType(),
                        tokenSection.getPathAdditionalProperties(),
                        tokenSection.getBodyAdditionalProperties()))
                // Privacy: doOnSuccess intentionally does NOT log the DTO contents (may contain secrets)
                .doOnSuccess(dto -> log.info("[AR-BFF][TPP_CREDENTIALS] Token-section credentials retrieved for entityId={}", entityId))
                .doOnError(e -> log.error("[AR-BFF][TPP_CREDENTIALS] Failed to retrieve token-section credentials for entityId={}: {}", entityId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TokenSectionDTOV1> updateTppCredentials(String entityId, TokenSectionDTOV1 tokenSectionDTO) {
        log.info("[AR-BFF][TPP_CREDENTIALS_UPDATE] Updating token-section credentials for entityId={}", entityId);
        return tppConnector.getTppByEntityId(entityId)
                .flatMap(response -> {
                    String tppId = response.getTppId();
                    log.info("[AR-BFF][TPP_CREDENTIALS_UPDATE] Resolved tppId={} for entityId={}", tppId, entityId);
                    // Privacy: body content (may contain client_secret) is never logged
                    return tppConnector.updateTppToken(tppId, new TokenSection(
                            tokenSectionDTO.getContentType(),
                            tokenSectionDTO.getPathAdditionalProperties(),
                            tokenSectionDTO.getBodyAdditionalProperties()));
                })
                .map(ts -> new TokenSectionDTOV1(
                        ts.getContentType(),
                        ts.getPathAdditionalProperties(),
                        ts.getBodyAdditionalProperties()))
                .doOnSuccess(dto -> log.info("[AR-BFF][TPP_CREDENTIALS_UPDATE] Token-section credentials updated for entityId={}", entityId))
                .doOnError(e -> log.error("[AR-BFF][TPP_CREDENTIALS_UPDATE] Failed to update token-section credentials for entityId={}: {}", entityId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppResponseDTOV1> patchTpp(String entityId, TppPatchDTOV1 patchDTO) {
        log.info("[AR-BFF][TPP_PATCH] Patching TPP for entityId={}", entityId);
        return tppConnector.getTppByEntityId(entityId)
                .flatMap(response -> {
                    String tppId = response.getTppId();
                    log.info("[AR-BFF][TPP_PATCH] Resolved tppId={} for entityId={}", tppId, entityId);
                    return tppConnector.patchTpp(tppId, TppConnectorMapper.toPatchRequest(patchDTO));
                })
                .map(TppConnectorMapper::toTppResponseDTOV1)
                .doOnSuccess(r -> log.info("[AR-BFF][TPP_PATCH] TPP patched successfully for entityId={}", entityId))
                .doOnError(e -> log.error("[AR-BFF][TPP_PATCH] Failed to patch TPP for entityId={}: {}", entityId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppSearchResponseDTOV1> searchTpp(String entityId, String businessName, int page, int size, List<String> fields) {
        log.info("[AR-BFF][TPP_SEARCH] Searching TPPs — entityId={}, businessName={}, page={}, size={}, fields={}",
                entityId != null ? "***" : null,
                businessName != null ? "***" : null,
                page, size, fields != null ? fields.size() : 0);
        return tppConnector.searchTpp(entityId, businessName, page, size, fields)
                .map(TppConnectorMapper::toTppSearchResponseDTOV1)
                .doOnSuccess(r -> log.info("[AR-BFF][TPP_SEARCH] Search completed: totalElements={}, totalPages={}",
                        r.getTotalElements(), r.getTotalPages()))
                .doOnError(e -> log.error("[AR-BFF][TPP_SEARCH] Search failed: {}", e.getMessage()));
    }

    /**
     * Resolves the Keycloak Client ID to use for a given TPP response, applying the
     * following priority:
     * <ol>
     *   <li><strong>Priority – legacy override:</strong> if {@code response.clientId} is
     *       present (non-null, non-empty) it is returned as-is. This covers pre-existing
     *       TPPs whose Keycloak client was provisioned with a random identifier.</li>
     *   <li><strong>Default:</strong> {@code response.entityId} is returned. This is the
     *       standard identifier used for all newly onboarded TPPs.</li>
     * </ol>
     *
     * <p><strong>Note:</strong> this method deliberately does NOT use {@code tppId}. The
     * old tppId-based lookup is replaced by entityId to align the Keycloak clientId with
     * the TPP's fiscal/VAT identifier going forward.</p>
     *
     * @param response the full TPP response from the emd-tpp connector
     * @return the resolved Keycloak {@code clientId} to use
     */
    private String resolveKeycloakClientId(TppEntityIdResponse response) {
        if (response.getClientId() != null && !response.getClientId().isEmpty()) {
            log.debug("[AR-BFF][KEYCLOAK_RESOLVE] Using legacy clientId override for tppId={}", response.getTppId());
            return response.getClientId();
        }
        return response.getEntityId();
    }

    /**
     * Compensating transaction: deletes the already-persisted TPP when Keycloak fails.
     * Always re-propagates the original Keycloak exception after compensation, regardless
     * of whether the delete succeeded or not.
     */
    private <T> Mono<T> compensateDelete(String tppId, Throwable keycloakException) {
        log.warn("[AR-BFF][TPP_CREATE] Keycloak failed for tppId={}, attempting compensating delete. Cause: {}",
                tppId, keycloakException.getMessage());
        return tppConnector.deleteTpp(tppId)
                .doOnSuccess(v -> log.info("[AR-BFF][TPP_CREATE] Compensating delete succeeded for tppId={}", tppId))
                .onErrorResume(deleteEx -> {
                    log.error("[AR-BFF][TPP_CREATE] Compensating delete ALSO failed for tppId={}. " +
                                "Manual reconciliation required. deleteError={}", tppId, deleteEx.getMessage());
                    return Mono.empty();
                })
                .then(Mono.error(keycloakException));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppDTOWithoutTokenSectionV1> updateTppState(String tppId, TppUpdateStateDTOV1 tppUpdateStateDTO) {
        log.info("[AR-BFF][TPP_UPDATE_STATE] Updating state for tppId={}", tppId);

        tppUpdateStateDTO.setTppId(tppId);

        return tppConnector.updateTppState(tppUpdateStateDTO)
            .map(TppConnectorMapper::toTppDTOWithoutTokenSectionV1)
            .doOnSuccess(r -> log.info("[AR-BFF][TPP_UPDATE_STATE] TPP updated successfully for tppId={}", tppId))
            .doOnError(e -> log.error("[AR-BFF][TPP_UPDATE_STATE] Failed to update TPP state for tppId={}: {}", tppId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> updateTppIsPaymentEnabled(String tppId, TppUpdateIsPaymentEnabledDTOV1 tppUpdateIsPaymentEnabledDTO) {
        log.info("[AR-BFF][TPP_IS_PAYMENT_ENABLED_UPDATE] Updating isPaymentEnabled for tppId={}", tppId);

        return tppConnector.updateTppIsPaymentEnabled(tppId, tppUpdateIsPaymentEnabledDTO)
            .doOnSuccess(r -> log.info("[AR-BFF][TPP_IS_PAYMENT_ENABLED_UPDATE] TPP updated successfully for tppId={}", tppId))
            .doOnError(e -> log.error("[AR-BFF][TPP_IS_PAYMENT_ENABLED_UPDATE] Failed to update TPP isPaymentEnabled for tppId={}: {}", tppId, e.getMessage()));
    }
    
    @Override
    public Mono<Void> insertRecipientIdOnWhitelist(String tppId, RecipientIdOnWhitelistDTOV1 recipientIdOnWhitelistDTO) {
        log.info("[AR-BFF][TPP_WHITELIST_ADD] Processing whitelist addition for tppId={}", tppId);

        return tppConnector.insertRecipientIdOnWhitelist(tppId, recipientIdOnWhitelistDTO)
            .doOnSuccess(v -> log.info("[AR-BFF][TPP_WHITELIST_ADD] Recipient added successfully for tppId={}", tppId))
            .doOnError(e -> log.error("[AR-BFF][TPP_WHITELIST_ADD] Failed to add recipient to whitelist for tppId={}: {}", tppId, e.getMessage()));
    }

    @Override
    public Mono<Void> removeRecipientIdOnWhitelist(String tppId, String recipientId) {
        log.info("[AR-BFF][TPP_WHITELIST_DELETE] Processing whitelist removal for tppId={}", tppId);

        return tppConnector.removeRecipientIdOnWhitelist(tppId, recipientId)
            .doOnSuccess(v -> log.info("[AR-BFF][TPP_WHITELIST_DELETE] Recipient removed successfully from tppId={}", tppId))
            .doOnError(e -> log.error("[AR-BFF][TPP_WHITELIST_DELETE] Failed to remove recipient from whitelist for tppId={}: {}", tppId, e.getMessage()));
    }

    @Override
    public Mono<Void> updateRecipientIdOnWhitelist(String tppId, List<String> recipientIds) {
        log.info("[AR-BFF][TPP_WHITELIST_UPDATE] Processing whitelist update for tppId={}", tppId);

        return tppConnector.updateRecipientIdOnWhitelist(tppId, recipientIds)
            .doOnSuccess(v -> log.info("[AR-BFF][TPP_WHITELIST_UPDATE] Whitelist updated successfully for tppId={}", tppId))
            .doOnError(e -> log.error("[AR-BFF][TPP_WHITELIST_UPDATE] Failed to update whitelist for tppId={}: {}", tppId, e.getMessage()));
    }
    
    @Override
    public Mono<TppConnectionResponseDTOV1> testAuthConnection(String tppId) {
        log.info("[AR-BFF][TPP_AUTH_TEST] Processing auth test request for tppId={}", tppId);
        return tppConnector.testAuthConnection(tppId)
            .doOnNext(r -> log.info("[AR-BFF][TPP_AUTH_TEST] Connection test completed for tppId={} with status: {}", tppId, r.getStatus()))
            .doOnError(e -> log.error("[AR-BFF][TPP_AUTH_TEST] Connection test failed for tppId={}: {}", tppId, e.getMessage()));
    }
}
