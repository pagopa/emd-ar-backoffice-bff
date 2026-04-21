package it.gov.pagopa.emd.ar.backoffice.service;

import org.springframework.http.ResponseEntity;

import it.gov.pagopa.emd.ar.backoffice.dto.AuthResponse;
import reactor.core.publisher.Mono;

public interface AuthService {

    Mono<ResponseEntity<AuthResponse>> getToken(String token);
}
