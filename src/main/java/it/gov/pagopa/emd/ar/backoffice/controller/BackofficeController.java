package it.gov.pagopa.emd.ar.backoffice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import it.gov.pagopa.emd.ar.backoffice.dto.ResponseDTO;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/emd/backoffice")
public interface BackofficeController {

    @PostMapping("api/auth/pagopa")
    public Mono<ResponseEntity<ResponseDTO>> getToken(@AuthenticationPrincipal Jwt jwt);
}
