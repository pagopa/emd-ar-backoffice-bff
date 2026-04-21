package it.gov.pagopa.emd.ar.backoffice.controller.v1;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import it.gov.pagopa.emd.ar.backoffice.dto.AuthRequestDTO;
import it.gov.pagopa.emd.ar.backoffice.dto.AuthResponse;
import reactor.core.publisher.Mono;

@RequestMapping("/emd/backoffice/v1")
public interface AuthControllerV1 {

    @PostMapping(value = "api/auth/pagopa", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AuthResponse>> getToken(@RequestBody AuthRequestDTO authRequest);
}
