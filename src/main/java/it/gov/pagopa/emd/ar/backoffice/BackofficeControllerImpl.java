package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackofficeControllerImpl implements BeckofficeController {

    @Override
    public String getTest() {
        System.out.println("test");
        return "test";
    }
    
}
