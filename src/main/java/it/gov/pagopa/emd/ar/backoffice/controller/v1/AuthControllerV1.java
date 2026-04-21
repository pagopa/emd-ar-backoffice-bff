package it.gov.pagopa.emd.ar.backoffice.controller.v1;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthResponseV1;
import reactor.core.publisher.Mono;

@RequestMapping("/emd/backoffice/api/v1")
public interface AuthControllerV1 {

    /**
     * Endpoint for exchanging an AR token with a Keycloak token
     * @param authRequest AR token
     * @return {@code Mono<ResponseEntity<AuthResponse>>} containing the AR user info and the
     *  Keycloak token or an error response
     */
    @PostMapping(value = "auth/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AuthResponseV1>> exchangeToken (@RequestBody AuthRequestDTOV1 authRequest);
}
