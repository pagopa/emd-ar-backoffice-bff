package it.gov.pagopa.emd.ar.backoffice.connector.citizen;

import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenSearchResponse;
import reactor.core.publisher.Mono;

/** Outbound adapter for the Citizen service. */
public interface CitizenConnector {

    /** Searches registered fiscal codes using the Citizen cursor-based pagination contract. */
    Mono<CitizenSearchResponse> searchByFiscalCode(String fiscalCode, String cursor, int size);
}

