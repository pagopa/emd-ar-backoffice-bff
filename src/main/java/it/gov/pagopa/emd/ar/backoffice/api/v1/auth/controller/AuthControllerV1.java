package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.UserPageResponseDTOV1;
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
     * Retrieves a paginated list of Keycloak users, optionally filtered by a search string.
     * The result includes the realm and client roles for each user.
     *
     * @param search an optional search string to filter users by username, email, first name, or last name
     * @param first  the index of the first result to return (used for pagination offset)
     * @param max    the maximum number of results to return (used for pagination limit)
     * @return {@code Mono<ResponseEntity<PageResponseDTOV1>>} containing the paginated list of users and the total count
     */
    @GetMapping("/users")
    Mono<ResponseEntity<UserPageResponseDTOV1>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "0") Integer first,
            @RequestParam(required = false, defaultValue = "5") Integer max
    );
}
