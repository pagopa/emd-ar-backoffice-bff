package it.gov.pagopa.emd.ar.backoffice.connector.citizen;

import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenConsentSearchResponse;
import reactor.core.publisher.Mono;

/** Outbound adapter for the Citizen service. */
public interface CitizenConnector {

    /** Searches registered fiscal codes using the Citizen cursor-based pagination contract. */
    Mono<CitizenSearchResponse> searchByFiscalCode(String fiscalCode, String cursor, int size);

    /** Searches all consents and enriched TPP details for one complete fiscal code. */
    Mono<CitizenConsentSearchResponse> searchCitizenConsents(String fiscalCode);

    /** Toggles the consent for one TPP and returns the updated citizen consent. */
    Mono<CitizenConsentSearchResponse> toggleCitizenConsent(String fiscalCode, String tppId);

    /** Deletes the consent aggregate and returns the snapshot supplied by Citizen. */
    Mono<CitizenConsentSearchResponse> deleteCitizenConsents(String fiscalCode);
}
