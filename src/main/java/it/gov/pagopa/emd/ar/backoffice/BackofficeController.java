package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/emd/backoffice")
public interface BackofficeController {

    @PostMapping("api/auth/pagopa")
    public ResponseEntity<ResponseDTO> getToken(@RequestBody RequestDTO token);

}

