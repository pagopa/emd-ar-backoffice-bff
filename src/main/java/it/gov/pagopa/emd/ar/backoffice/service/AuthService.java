package it.gov.pagopa.emd.ar.backoffice.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthService {

    // Recuperiamo l'issuer dal file application.yml
    @Value("${auth.expected-issuer}")
    private String expectedIssuer;

    // Default for test
    @Value("${auth.expected-audience:account}")
    private String expectedAudience;

    public boolean verifyTokenFields(String token) {
        log.debug("verifyTokenFields()");
        try {
            // Decodifica il token
            DecodedJWT jwt = JWT.decode(token);

            // Controllo issuer (iss)
            String issuer = jwt.getIssuer();
            if (!expectedIssuer.equals(issuer)) {
                log.warn("Validazione fallita: Issuer non corrispondente. Ricevuto: {}", issuer);
                return false;
            }

            // Controllo audience (aud)
            List<String> audiences = jwt.getAudience();
            if (audiences == null || !audiences.contains(expectedAudience)) {
                log.warn("Validazione fallita: Audience '{}' non trovata nel token.", expectedAudience);
                return false;
            }

            log.info("Validazione token riuscita per sub: {}", jwt.getSubject());
            return true;

        } catch (Exception e) {
            log.error("Errore durante la decodifica del token: {}", e.getMessage());
            return false;
        }
    }
}