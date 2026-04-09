package it.gov.pagopa.emd.ar.backoffice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.gov.pagopa.emd.ar.backoffice.dto.ResponseDTO;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/emd/backoffice")
public interface BackofficeController {

    @PostMapping("api/auth/pagopa")
    public Mono<ResponseEntity<ResponseDTO>> getToken(@RequestHeader("Authorization") String authHeader);

}
