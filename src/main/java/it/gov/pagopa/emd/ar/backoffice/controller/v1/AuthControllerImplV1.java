package it.gov.pagopa.emd.ar.backoffice.controller.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.service.AuthServiceImpl;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class AuthControllerImplV1 implements AuthControllerV1 {
    
    private AuthServiceImpl backofficeService;

    public AuthControllerImplV1(AuthServiceImpl backofficeService) {
        this.backofficeService = backofficeService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ResponseEntity<AuthResponseV1>> exchangeToken(AuthRequestDTOV1 authRequest) {
        return backofficeService.exchangeToken(authRequest.getToken());
    }
    
}
