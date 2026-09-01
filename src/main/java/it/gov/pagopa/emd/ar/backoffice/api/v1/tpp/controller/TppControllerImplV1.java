package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.controller;

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
import it.gov.pagopa.emd.ar.backoffice.service.tpp.TppService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@Slf4j
public class TppControllerImplV1 implements TppControllerV1 {

    private final TppService tppService;

    public TppControllerImplV1(TppService tppService) {
        this.tppService = tppService;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppResponseDTOV1>> saveTpp(String entityId, TppDTOV1 tppDTO) {
        log.info("[AR-BFF][TPP_SAVE] Saving TPP for entityId={}", entityId);
        return tppService.createTppAndKeycloakClient(entityId, tppDTO)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppResponseDTOV1>> getTppByEntityId(String entityId, boolean detailed) {
        log.info("[AR-BFF][TPP_GET] Getting TPP by entityId={}, detailed={}", entityId, detailed);
        return tppService.getTppByEntityId(entityId, detailed)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<Void>> deleteTpp(String entityId) {
        log.info("[AR-BFF][TPP_DELETE] Deleting TPP and Keycloak client for entityId={}", entityId);
        return tppService.deleteTppAndKeycloakClient(entityId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppPagopaCredentialsDTOV1>> getTppPagopaCredentials(String entityId) {
        log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Getting PagoPA credentials for entityId={}", entityId);
        return tppService.getTppPagopaCredentials(entityId)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TokenSectionDTOV1>> getTppCredentials(String entityId) {
        log.info("[AR-BFF][TPP_CREDENTIALS] Getting token-section credentials for entityId={}", entityId);
        return tppService.getTppCredentials(entityId)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TokenSectionDTOV1>> updateTppCredentials(String entityId, TokenSectionDTOV1 tokenSectionDTO) {
        log.info("[AR-BFF][TPP_CREDENTIALS_UPDATE] Updating token-section credentials for entityId={}", entityId);
        // Privacy: tokenSectionDTO is NOT logged — may contain client_secret
        return tppService.updateTppCredentials(entityId, tokenSectionDTO)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppResponseDTOV1>> patchTpp(String entityId, TppPatchDTOV1 patchDTO) {
        log.info("[AR-BFF][TPP_PATCH] Patching TPP for entityId={}", entityId);
        return tppService.patchTpp(entityId, patchDTO)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppSearchResponseDTOV1>> searchTpp(
            String entityId, String businessName, int page, int size, List<String> fields) {
        log.info("[AR-BFF][TPP_SEARCH] Searching TPPs — entityId={}, businessName={}, page={}, size={}, fields={}",
                entityId != null ? "***" : null,
                businessName != null ? "***" : null,
                page, size, fields != null ? fields.size() : 0);
        return tppService.searchTpp(entityId, businessName, page, size, fields)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppDTOWithoutTokenSectionV1>> updateTppState(String tppId, TppUpdateStateDTOV1 tppUpdateStateDTO) {
        log.info("[AR-BFF][TPP_STATE_UPDATE] Updating TPP state for tppId={}", tppId);
        return tppService.updateTppState(tppId, tppUpdateStateDTO)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<Void>> updateTppIsPaymentEnabled(String tppId, TppUpdateIsPaymentEnabledDTOV1 tppUpdateIsPaymentEnabledDTO) {
        log.info("[AR-BFF][TPP_PAYMENT_ENABLED_UPDATE] Updating TPP payment enabled status for tppId={}", tppId);
        return tppService.updateTppIsPaymentEnabled(tppId, tppUpdateIsPaymentEnabledDTO)
                .thenReturn(ResponseEntity.noContent().build());
    }
                
    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ResponseEntity<Void>> insertRecipientIdOnWhitelist(String tppId, RecipientIdOnWhitelistDTOV1 recipientIdOnWhitelistDTO) {
        log.info("[AR-BFF][TPP_WHITELIST_ADD] Adding recipientId={} to whitelist for tppId={}", 
                recipientIdOnWhitelistDTO.getRecipientId(), tppId);
        return tppService.insertRecipientIdOnWhitelist(tppId, recipientIdOnWhitelistDTO)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ResponseEntity<Void>> removeRecipientIdOnWhitelist(String tppId, String recipientId) {
        log.info("[AR-BFF][TPP_WHITELIST_DELETE] Removing recipientId={} from whitelist for tppId={}", recipientId, tppId);
        return tppService.removeRecipientIdOnWhitelist(tppId, recipientId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<Void>> updateRecipientIdOnWhitelist(String tppId, List<String> recipientIds) {
        log.info("[AR-BFF][TPP_WHITELIST_UPDATE] Updating whitelist for tppId={}", tppId);
        return tppService.updateRecipientIdOnWhitelist(tppId, recipientIds)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppConnectionResponseDTOV1>> testAuthConnection(String tppId) {
        log.info("[AR-BFF][TPP_AUTH_TEST] Initiating connection test for tppId={}", tppId);
        return tppService.testAuthConnection(tppId)
                .map(ResponseEntity::ok);
    }
}
