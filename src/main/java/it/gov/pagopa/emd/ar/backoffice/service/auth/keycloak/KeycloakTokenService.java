package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import reactor.core.publisher.Mono;

/**
 * Manages Keycloak access tokens needed by the application:
 * <ul>
 *   <li>Manager token via {@code client_credentials} grant (cached + proactively refreshed).</li>
 *   <li>User token via {@code urn:ietf:params:oauth:grant-type:jwt-bearer} grant (token exchange).</li>
 * </ul>
 */
public interface KeycloakTokenService {

    /**
     * Returns a valid Keycloak manager token, using the in-memory cache when possible.
     *
     * @return {@code Mono<String>} containing the access token
     */
    Mono<String> getManagerToken();

    /**
     * Exchanges the provided external JWT for a Keycloak token via the JWT-Bearer grant.
     *
     * @param externalToken the raw external JWT to exchange
     * @return {@code Mono<String>} containing the resulting Keycloak access token
     */
    Mono<String> getJwtBearerToken(String externalToken);
}

