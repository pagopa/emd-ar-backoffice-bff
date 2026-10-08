package it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenConsentSearchRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenConsentSearchResponseDTOV1;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

/** Version 1 Citizen-facing endpoints. */
@RequestMapping("/emd/backoffice/api/v1")
public interface CitizenControllerV1 {

    /**
     * Searches registered fiscal codes with keyset pagination. Pass the prior response's
     * {@code nextCursor} unchanged as {@code cursor} to retrieve the next page.
     */
    @GetMapping(value = "citizen/search", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<CitizenSearchResponseDTOV1>> searchByFiscalCode(
            @RequestParam(name = "fiscalCode") String fiscalCode,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", required = false) String size);

    /** Retrieves enriched consent details for one complete fiscal code supplied in the request body. */
    @PostMapping(value = "citizen/consent/search", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<CitizenConsentSearchResponseDTOV1>> searchCitizenConsents(
            @Valid @RequestBody CitizenConsentSearchRequestDTOV1 request);
}

