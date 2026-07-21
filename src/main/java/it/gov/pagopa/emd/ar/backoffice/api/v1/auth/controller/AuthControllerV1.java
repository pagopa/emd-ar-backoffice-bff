package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AdminAuthRequestV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
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


    // -----------------------------------------------------------------------------------------------
    // --------------------------------------Admin Portal---------------------------------------------
    /**
     * Gets a Keycloak token for the Admin Portal using the provided authorization code and code verifier.
     * @param request
     * @return
     */
    @PostMapping(value = "auth/admin/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<Void>>adminAuth(@RequestBody AdminAuthRequestV1 request);
    

}
