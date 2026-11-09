package it.gov.pagopa.emd.ar.backoffice.service.auth;

import java.util.List;

import org.springframework.http.ResponseEntity;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.UserPageResponseDTOV1;
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
     * Orchestrates the retrieval of a paginated list of Keycloak users.
     * It fetches the manager token and then requests the users and their total count from Keycloak.
     *
     * @param search an optional string to filter the users
     * @param first  the index of the first user to return
     * @param max    the maximum number of users to retrieve
     * @return {@code Mono<ResponseEntity<PageResponseDTOV1>>} containing the paginated users data
     */
    Mono<ResponseEntity<UserPageResponseDTOV1>> getUsers(String search, Integer first, Integer max);

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
     * Keycloak does not expose an atomic "replace/PUT" endpoint for role mappings, so the
     * replacement is orchestrated as a read-modify-write sequence. The whole sequence runs under
     * a distributed lock keyed by user/client, so two concurrent replacements for the same
     * user/client cannot interleave across replicas and leave the union of both requested sets.
     * The steps are:
     * <ol>
     *   <li>Validate that every requested role exists on the target client, before any mutation.</li>
     *   <li>Fetch the user's current roles.</li>
     *   <li>Add the requested roles via POST (add-before-remove), then remove the roles that are
     *       no longer requested via DELETE. This ordering ensures a failure never strips the user
     *       of permissions.</li>
     *   <li>Re-read the roles and confirm they match the requested set; otherwise fail with an
     *       explicit error instead of reporting a misleading success.</li>
     * </ol>
     * Passing an empty list removes all client roles from the user.
     *
     * @param userId the internal Keycloak UUID of the target user
     * @param roles  the new list of roles to be assigned to the user
     * @return a {@code Mono<ResponseEntity<Void>>} completing with 204 when the final state is
     *         confirmed, or failing if the replacement could not be confirmed
     */
    Mono<ResponseEntity<Void>> replaceUserRoles(String userId, List<KeycloakRoleDTOV1> roles);
}
