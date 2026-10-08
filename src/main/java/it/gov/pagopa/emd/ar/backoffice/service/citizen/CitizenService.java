package it.gov.pagopa.emd.ar.backoffice.service.citizen;

import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenConsentSearchResponseDTOV1;
import reactor.core.publisher.Mono;

/** Application service for Citizen-related operations. */
public interface CitizenService {

    /** Searches fiscal codes using cursor-based pagination. */
    Mono<CitizenSearchResponseDTOV1> searchByFiscalCode(String fiscalCode, String cursor, String size);

    /** Retrieves enriched consent details for one complete fiscal code. */
    Mono<CitizenConsentSearchResponseDTOV1> searchCitizenConsents(String fiscalCode);
}

