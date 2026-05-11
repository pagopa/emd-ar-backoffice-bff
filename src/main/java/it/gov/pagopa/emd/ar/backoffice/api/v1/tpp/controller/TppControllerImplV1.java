package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.tpp.TppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class TppControllerImplV1 implements TppControllerV1 {

    private final TppService tppService;

    public TppControllerImplV1(TppService tppService) {
        this.tppService = tppService;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppIdResponseDTOV1>> saveTpp(TppDTOV1 tppDTO) {
        log.info("[AR-BFF][TPP_SAVE] Saving TPP");
        return tppService.createTppAndKeycloakClient(tppDTO)
                .map(tppId -> ResponseEntity.ok(new TppIdResponseDTOV1(tppId)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppIdResponseDTOV1>> getTppByEntityId(String entityId) {
        log.info("[AR-BFF][TPP_GET] Getting TPP by entityId={}", entityId);
        return tppService.getTppByEntityId(entityId)
                .map(ResponseEntity::ok);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<Void>> deleteTpp(String tppId) {
        log.info("[AR-BFF][TPP_DELETE] Deleting TPP and Keycloak client for tppId={}", tppId);
        return tppService.deleteTppAndKeycloakClient(tppId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<TppPagopaCredentialsDTOV1>> getTppPagopaCredentials(String tppId) {
        log.info("[AR-BFF][TPP_PAGOPA_CREDENTIALS] Getting PagoPA credentials for tppId={}", tppId);
        return tppService.getTppPagopaCredentials(tppId)
                .map(ResponseEntity::ok);
    }
}
