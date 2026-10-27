package it.gov.pagopa.emd.ar.backoffice.service.auth;

import org.springframework.http.ResponseEntity;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.PageResponseDTOV1;
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
    Mono<ResponseEntity<PageResponseDTOV1>> getUsers(String search, Integer first, Integer max);
}
