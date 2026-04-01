package it.gov.pagopa.emd.ar.backoffice;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class BackofficeControllerImpl implements BackofficeController {

    @Override
    public ResponseEntity<ResponseDTO> getToken(RequestDTO token) {
        String tokenString = token.getToken();
        log.info("getToken(" + tokenString + ")");

        // Validazione base del formato
        if (tokenString == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ResponseDTO("ERROR","Token mancante", StringUtils.EMPTY));
        }
        
        if (!isValidFormat(tokenString)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ResponseDTO("ERROR","Token mancante o formato errato", tokenString));
        }

        return ResponseEntity.ok(new ResponseDTO("Success","Token received successfully", token.getToken()));
    }


    private boolean isValidFormat(String token) {
        // Il token deve essere un JWT (3 parti separate da punti) o una stringa alfanumerica di 32 caratteri
        return token.matches("^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*$");
    }
    
}
