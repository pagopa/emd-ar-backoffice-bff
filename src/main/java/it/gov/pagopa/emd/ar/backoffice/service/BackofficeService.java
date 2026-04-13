package it.gov.pagopa.emd.ar.backoffice.service;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import it.gov.pagopa.emd.ar.backoffice.dto.ResponseDTO;
import reactor.core.publisher.Mono;

public interface BackofficeService {

    Mono<ResponseEntity<ResponseDTO>> getToken(Jwt jwt);
}
