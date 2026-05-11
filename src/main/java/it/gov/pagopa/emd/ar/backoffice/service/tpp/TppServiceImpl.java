package it.gov.pagopa.emd.ar.backoffice.service.tpp;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.mapper.TppConnectorMapper;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Orchestrates TPP creation:
 * <ol>
 *   <li>Maps the API DTO to the connector request (applying defaults via {@link TppConnectorMapper}).</li>
 *   <li>Persists the TPP via the connector (obtains the {@code tppId}).</li>
 *   <li>Creates the corresponding Keycloak OIDC client using the {@code tppId}.</li>
 * </ol>
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
    public Mono<String> createTppAndKeycloakClient(TppDTOV1 tppDTO) {
        log.info("[AR-BFF][TPP_CREATE] Creating new TPP: {}", tppDTO.getBusinessName());
        return tppConnector.saveTpp(TppConnectorMapper.toCreateRequest(tppDTO))
                .flatMap(tppId -> {
                    log.info("[AR-BFF][TPP_CREATE] TPP persisted with id={}. Creating Keycloak client.", tppId);
                    return keycloakClientService.createKeycloakClient(tppId, tppDTO.getEntityId(), tppDTO.getBusinessName())
                            .thenReturn(tppId)
                            .onErrorResume(ex -> compensateDelete(tppId, ex));
                })
                .doOnSuccess(tppId -> log.info("[AR-BFF][TPP_CREATE] TPP creation completed. id={}", tppId))
                .doOnError(e -> log.error("[AR-BFF][TPP_CREATE] Error during TPP creation: {}", e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppIdResponseDTOV1> getTppByEntityId(String entityId) {
        log.info("[AR-BFF][TPP_GET] Looking up TPP by entityId={}", entityId);
        return tppConnector.getTppByEntityId(entityId)
                .map(response -> new TppIdResponseDTOV1(response.tppId()))
                .doOnSuccess(r -> log.info("[AR-BFF][TPP_GET] Found TPP tppId={} for entityId={}", r.getTppId(), entityId))
                .doOnError(e -> log.warn("[AR-BFF][TPP_GET] TPP not found for entityId={}: {}", entityId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> deleteTppAndKeycloakClient(String tppId) {
        log.info("[AR-BFF][TPP_DELETE] Deleting TPP and Keycloak client for tppId={}", tppId);
        return keycloakClientService.deleteKeycloakClient(tppId)
                .then(Mono.defer(() -> tppConnector.deleteTpp(tppId)))
                .doOnSuccess(v -> log.info("[AR-BFF][TPP_DELETE] TPP and Keycloak client deleted for tppId={}", tppId))
                .doOnError(e -> log.error("[AR-BFF][TPP_DELETE] Error during TPP deletion for tppId={}: {}", tppId, e.getMessage()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<TppPagopaCredentialsDTOV1> getTppPagopaCredentials(String entityId) {
        log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Retrieving PagoPA credentials for entityId={}", entityId);
        return tppConnector.getTppByEntityId(entityId)
                .flatMap(response -> {
                    log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Resolved tppId={} for entityId={}", response.tppId(), entityId);
                    return keycloakClientService.getPagopaClientCredentials(response.tppId());
                })
                .doOnSuccess(c -> log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] PagoPA credentials retrieved for entityId={}", entityId))
                .doOnError(e -> log.error("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Failed to retrieve PagoPA credentials for entityId={}: {}", entityId, e.getMessage()));
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
}
