package it.gov.pagopa.emd.ar.backoffice.service.auth;

import java.util.List;

import org.springframework.http.ResponseEntity;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;
import reactor.core.publisher.Mono;

public interface AuthService {

    /**
     * This method will decode the AR token, verify its fields, 
     * upsert the corresponding user in Keycloak and return an AuthResponse containing the user info and a Keycloak token.
     * 
     * @param token AR token to be exchanged
     * @return {@code Mono<ResponseEntity<AuthResponse>>} containing the AR user info and the
     *  Keycloak token or an error response
     */
    Mono<ResponseEntity<AuthResponseV1>> exchangeToken(String token);

    /**
     * Fetches the complete list of available roles for the configured target Keycloak client.
     * The service internally resolves the client's UUID before retrieving its roles.
     *
     * @return a {@code Mono<ResponseEntity>} containing the list of {@link KeycloakRoleDTOV1}
     */
    Mono<ResponseEntity<List<KeycloakRoleDTOV1>>> getAvailableRoles();

    /**
     * Replaces the assigned client roles for a specified user in Keycloak.
     * <p>
     * Because Keycloak does not natively expose a "replace/PUT" endpoint for role mappings,
     * this method orchestrates the replacement by sequentially:
     * <ol>
     *   <li>Fetching the user's current roles.</li>
     *   <li>Issuing a DELETE request to wipe the current roles (if any).</li>
     *   <li>Issuing a POST request to assign the newly requested roles (if not empty).</li>
     * </ol>
     * </p>
     *
     * @param userId the internal Keycloak UUID of the target user
     * @param roles  the new list of roles to be assigned to the user
     * @return a {@code Mono<ResponseEntity<Void>>} completing when the Keycloak operations succeed
     */
    Mono<ResponseEntity<Void>> replaceUserRoles(String userId, List<KeycloakRoleDTOV1> roles);
}
