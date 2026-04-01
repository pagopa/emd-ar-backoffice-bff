package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class BackofficeControllerImpl implements BeckofficeController {

    @Override
    public ResponseEntity<ResponseDTO>  getToken(String token) {
        log.info("getToken(" + token + ")");

        // Validazione base del formato
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ResponseDTO("ERROR","Token mancante o formato errato"));
        }
        
        // Rimuove "Bearer "
        String actualToken = token.substring(7);
        
        if (!isValidFormat(actualToken)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ResponseDTO("ERROR","Token mancante o formato errato"));
        }

        return ResponseEntity.ok(new ResponseDTO("Success","Token received successfully"));
    }


    private boolean isValidFormat(String token) {
        // Il token deve essere un JWT (3 parti separate da punti) o una stringa alfanumerica di 32 caratteri
        return token.matches("^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*$");
    }
    
}
