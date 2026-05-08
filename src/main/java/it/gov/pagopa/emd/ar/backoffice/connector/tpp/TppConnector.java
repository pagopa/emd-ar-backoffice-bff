package it.gov.pagopa.emd.ar.backoffice.connector.tpp;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import reactor.core.publisher.Mono;

/**
 * Abstraction for the outbound TPP persistence adapter.
 */
public interface TppConnector {

    /**
     * Saves TPP data by sending a POST request to the remote emd-tpp service.
     *
     * @param request the outbound payload (already enriched with defaults)
     * @return {@code Mono<String>} containing the tppId of the saved TPP or an error if the operation fails
     */
    Mono<String> saveTpp(TppCreateRequest request);

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
}
