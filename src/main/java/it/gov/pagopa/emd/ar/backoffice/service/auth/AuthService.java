package it.gov.pagopa.emd.ar.backoffice.service.auth;

import org.springframework.http.ResponseEntity;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
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
     * Renews the Keycloak session using the provided refresh token.
     * 
     * @param refreshToken the Keycloak refresh token obtained during exchange or previous refresh
     * @return {@code Mono<ResponseEntity<AuthResponseV1>>} with new tokens or 401 if the session is expired
     */
    Mono<ResponseEntity<AuthResponseV1>> refreshToken(String refreshToken);
}
