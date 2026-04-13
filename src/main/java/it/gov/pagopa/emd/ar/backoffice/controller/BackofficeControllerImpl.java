package it.gov.pagopa.emd.ar.backoffice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;

import it.gov.pagopa.emd.ar.backoffice.dto.ResponseDTO;
import it.gov.pagopa.emd.ar.backoffice.service.BackofficeServiceImpl;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class BackofficeControllerImpl implements BackofficeController {
    
    private BackofficeServiceImpl backofficeService;

    public BackofficeControllerImpl(BackofficeServiceImpl backofficeService) {
        this.backofficeService = backofficeService;
    }

    @Override
    public Mono<ResponseEntity<ResponseDTO>> getToken(Jwt jwt) {
        return backofficeService.getToken(jwt);
    }
    
}
