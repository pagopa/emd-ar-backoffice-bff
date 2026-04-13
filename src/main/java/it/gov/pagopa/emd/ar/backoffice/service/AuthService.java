package it.gov.pagopa.emd.ar.backoffice.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import it.gov.pagopa.emd.ar.backoffice.dto.OrganizationDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

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

    public boolean verifyTokenFields(Jwt jwt) {
        log.info("AuthService - verifyTokenFields()");
        
        //Recupera la mappa "organization" dal token
        Map<String, Object> organizationMap = jwt.getClaim("organization");

        if (organizationMap == null) {
            log.warn("AuthService - Validazione token fallita per sub: {}", jwt.getSubject());
            return false;
        }

        ObjectMapper mapper = new ObjectMapper();
        OrganizationDTO org = mapper.convertValue(organizationMap, OrganizationDTO.class);

        String orgId = org.getId();
        String orgName = org.getName();
        String firstRole = org.getRoles().get(0).getRole();

        System.out.println("Organization ID: " + orgId);
        System.out.println("Organization Name: " + orgName);
        System.out.println("First Role: " + firstRole);


        log.info("AuthService - Validazione token riuscita per sub: {}", jwt.getSubject());
        return true;
    }
}