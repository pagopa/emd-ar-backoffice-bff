package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

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
     * @param clientId the identifier for the new Keycloak client (typically the TPP ID)
     * @param entityId the TPP entity ID to embed as a hardcoded claim in the client's token mapper
     * @return {@code Mono<String>} emitting the {@code clientId} upon successful creation
     */
    Mono<String> createKeycloakClient(String clientId, String entityId);
}

