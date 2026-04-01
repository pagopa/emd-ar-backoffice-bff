package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice") 
public interface BeckofficeController {

    @GetMapping("/auth/api/auth/pagopa")
    public ResponseEntity<ResponseDTO> getToken(@RequestBody RequestDTO token);

}

