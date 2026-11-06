package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;
import reactor.core.publisher.Mono;
import java.util.List;

public interface KeycloakRoleService {

    /**
     * Retrieves all available roles configured for the target Keycloak client 
     *
     * @return a {@code Mono<List<KeycloakRoleDTOV1>>} containing the roles available for the client
     */
    Mono<List<KeycloakRoleDTOV1>> getAvailableClientRoles();

    /**
     * Replaces the current client roles of a user with the provided list of roles.
     * <p>
     * This operation ensures that the user's role mapping perfectly matches the provided list.
     * Any existing roles not in the list will be removed, and new roles will be added.
     * Passing an empty list will remove all client roles from the user.
     * </p>
     *
     * @param userId the internal Keycloak UUID of the target user
     * @param roles  the new list of roles to assign (each role must contain a valid id and name)
     * @return a {@code Mono<Void>} that completes when the synchronization is successful
     */
    Mono<Void> replaceUserRoles(String userId, List<KeycloakRoleDTOV1> roles);
}
