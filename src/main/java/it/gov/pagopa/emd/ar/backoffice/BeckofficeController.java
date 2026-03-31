package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test") 
public interface BeckofficeController {

    @GetMapping()
    String getTest();
}

