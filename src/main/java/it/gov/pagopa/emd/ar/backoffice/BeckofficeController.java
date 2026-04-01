package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice") 
public interface BeckofficeController {

    @GetMapping("/auth/api/auth/pagopa")
    public ResponseEntity<ResponseDTO> getToken(@RequestHeader("Authorization") String token);

}

