package it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenConsentSearchRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenConsentSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.citizen.CitizenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** REST controller delegating fiscal-code searches to the application service. */
@RestController
@Slf4j
public class CitizenControllerImplV1 implements CitizenControllerV1 {

    private final CitizenService citizenService;

    public CitizenControllerImplV1(CitizenService citizenService) {
        this.citizenService = citizenService;
    }

    @Override
    public Mono<ResponseEntity<CitizenSearchResponseDTOV1>> searchByFiscalCode(
            String fiscalCode, String cursor, String size) {
        log.info("[AR-BFF][CITIZEN_SEARCH] Request received (cursorPresent={}, sizeProvided={})",
                cursor != null, size != null);
        return citizenService.searchByFiscalCode(fiscalCode, cursor, size)
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<CitizenConsentSearchResponseDTOV1>> searchCitizenConsents(
            CitizenConsentSearchRequestDTOV1 request) {
        log.info("[AR-BFF][CITIZEN_CONSENT_SEARCH] Request received");
        return citizenService.searchCitizenConsents(request.getFiscalCode())
                .map(ResponseEntity::ok);
    }
}

