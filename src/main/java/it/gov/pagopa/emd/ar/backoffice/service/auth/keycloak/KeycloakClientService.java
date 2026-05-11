package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import reactor.core.publisher.Mono;

/**
 * Manages Keycloak OIDC client provisioning for TPP onboarding.
 */
public interface KeycloakClientService {

    /**
     * Creates a new confidential OIDC client in Keycloak, associates its service-account user
     * with the configured TPP group, and attaches a hardcoded-claim protocol mapper so that
     * every token issued for this client carries the TPP {@code entityId} as a claim.
     *
     * @param clientId     the identifier for the new Keycloak client (typically the TPP ID)
     * @param entityId     the TPP entity ID to embed as a hardcoded claim in the client's token mapper
     * @param businessName the human-readable TPP business name used as the Keycloak client display name
     * @return {@code Mono<String>} emitting the {@code clientId} upon successful creation
     */
    Mono<String> createKeycloakClient(String clientId, String entityId, String businessName);

    /**
     * Deletes the Keycloak OIDC client identified by the given {@code clientId} (i.e. the TPP ID).
     * If no client with that {@code clientId} exists in Keycloak the operation completes as a no-op.
     *
     * @param clientId the Keycloak {@code clientId} to delete (equals the TPP ID)
     * @return {@code Mono<Void>} completing when the client has been deleted
     */
    Mono<Void> deleteKeycloakClient(String clientId);

    /**
     * Retrieves the PagoPA credentials (client ID and client secret) for the Keycloak OIDC
     * client identified by {@code clientId} (i.e. the TPP ID).
     *
     * <p>Internally performs two sequential calls to Keycloak Admin API:
     * <ol>
     *   <li>Resolves the Keycloak internal UUID via {@code GET /clients?clientId=…&exact=true}</li>
     *   <li>Fetches the secret via {@code GET /clients/{internalId}/client-secret}</li>
     * </ol>
     * If no client is found for {@code clientId}, a
     * {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException} (404)
     * is emitted.</p>
     *
     * <p><strong>Privacy:</strong> the returned {@code clientSecret} must never be logged.</p>
     *
     * @param clientId the Keycloak {@code clientId} (equals the TPP ID)
     * @return {@code Mono<TppPagopaCredentialsDTOV1>} with the resolved credentials
     */
    Mono<TppPagopaCredentialsDTOV1> getPagopaClientCredentials(String clientId);
}

