package it.gov.pagopa.emd.ar.backoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import it.gov.pagopa.emd.ar.backoffice.dto.OrganizationDTO;
import it.gov.pagopa.emd.ar.backoffice.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthService {

    private final ObjectMapper objectMapper;

    public AuthService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UserDTO verifyTokenFields(Jwt jwt) {
        log.info("AuthService - verifyTokenFields() for sub: {}", jwt.getSubject());
        UserDTO user = new UserDTO();
        
        try{
            //Recupera la mappa "organization" dal token
            Map<String, Object> organizationMap = jwt.getClaim("organization");

            if (organizationMap == null) {
                log.warn("AuthService - Validazione token fallita per sub: {}", jwt.getSubject());
                return null;
            }

            OrganizationDTO org = objectMapper.convertValue(organizationMap, OrganizationDTO.class);

            user.setName(jwt.getClaimAsString("name"));
            user.setFamilyName(jwt.getClaimAsString("family_name"));
            user.setEmail(jwt.getClaimAsString("email"));
            user.setUid(jwt.getClaimAsString("uid"));
            user.setOrganization(org);

            log.info("AuthService - Validazione token riuscita per sub: {}", jwt.getSubject());
            return user;
        } catch (Exception e) {
            log.error("AuthService - Errore durante la validazione del token per sub: {}: {}", jwt.getSubject(), e.getMessage());
            return null;
        }
        
    }
}