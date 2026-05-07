package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.service.auth.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class AuthControllerImplV1 implements AuthControllerV1 {

    private final AuthService authService;

    public AuthControllerImplV1(AuthService authService) {
        this.authService = authService;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<AuthResponseV1>> exchangeToken(AuthRequestDTOV1 authRequest) {
        return authService.exchangeToken(authRequest.getToken());
    }
}
