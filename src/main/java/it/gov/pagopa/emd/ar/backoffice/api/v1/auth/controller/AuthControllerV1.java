package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;
import reactor.core.publisher.Mono;

@RequestMapping("/emd/backoffice/api/v1")
@Validated
public interface AuthControllerV1 {

    /**
     * Exchanges a SelfCare token for a Keycloak session token (JWT Bearer Grant).
     * The backend validates the incoming SelfCare token, performs JIT user provisioning,
     * and returns a Keycloak token along with the user info.
     *
     * @param authRequest request body containing the SelfCare token
     * @return {@code Mono<ResponseEntity<AuthResponseV1>>} containing the user info and the
     *  Keycloak token, or an error response
     */
    @PostMapping(value = "auth/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<AuthResponseV1>> exchangeToken(@Valid @RequestBody AuthRequestDTOV1 authRequest);

    /**
     * Retrieves all available roles defined in Keycloak for the target backoffice portal client.
     * These roles can be used to populate frontend selection menus before assigning them to a user.
     *
     * @return a {@code Mono<ResponseEntity<List<KeycloakRoleDTOV1>>>} containing the list of available roles.
     */
    @GetMapping("/roles")
    Mono<ResponseEntity<List<KeycloakRoleDTOV1>>> getAvailableRoles();

    /**
     * Completely replaces the current client roles of a specific user in Keycloak.
     * <p>
     * This operation performs a full synchronization: any existing roles that are not present
     * in the provided list will be removed, and the new ones will be added. Passing an empty list
     * will remove all client roles from the user.
     * </p>
     *
     * @param userId the internal Keycloak UUID of the user (not the external SelfCare UID)
     * @param roles  the complete list of roles the user should possess. Each role object must 
     *               contain at least a valid {@code id} and {@code name}.
     * @return a {@code Mono<ResponseEntity<Void>>} returning 204 NO_CONTENT on successful replacement
     */
    @PostMapping(value = "/users/{userId}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<Void>> replaceUserRoles(
            @PathVariable("userId") @NotBlank String userId,
            @RequestBody @NotNull List<KeycloakRoleDTOV1> roles);
}
