package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import reactor.core.publisher.Mono;

/**
 * Manages Keycloak OIDC client provisioning for TPP onboarding.
 */
public interface KeycloakClientService {

    /**
     * Creates a new confidential OIDC client in Keycloak and associates its service-account user
     * with the configured TPP group.
     *
     * @param clientId the identifier for the new Keycloak client (typically the TPP ID)
     * @return {@code Mono<String>} emitting the {@code clientId} upon successful creation
     */
    Mono<String> createKeycloakClient(String clientId);
}

