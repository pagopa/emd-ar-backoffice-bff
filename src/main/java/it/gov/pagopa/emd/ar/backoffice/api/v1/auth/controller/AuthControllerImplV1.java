package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.controller;

import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.UserPageResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.auth.AuthService;
import lombok.extern.slf4j.Slf4j;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;

import java.util.List;

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

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<UserPageResponseDTOV1>> getUsers(String search, Integer first, Integer max) {
        log.info("[AR-BFF][GET_USERS] Start: search={}, first={}, max={}", search, first, max);
        return authService.getUsers(search, first, max);
    }

    /** {@inheritDoc} */
    public Mono<ResponseEntity<List<KeycloakRoleDTOV1>>> getAvailableRoles() {
        return authService.getAvailableRoles();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<Void>> replaceUserRoles(String userId, List<KeycloakRoleDTOV1> roles, String authHeader) {
        String userEmail = Utilities.getEmailFromToken(authHeader);
        log.info("[AR-BFF][REPLACE_USER_ROLES][User:{}] Request received - Replacing roles for userId={}", userEmail, userId);
        return authService.replaceUserRoles(userId, roles);
    }
}
