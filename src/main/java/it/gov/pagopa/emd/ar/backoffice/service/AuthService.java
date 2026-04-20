package it.gov.pagopa.emd.ar.backoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import it.gov.pagopa.emd.ar.backoffice.dto.OrganizationDTO;
import it.gov.pagopa.emd.ar.backoffice.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthService {

    private final ObjectMapper objectMapper;

    public AuthService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<UserDTO> verifyTokenFields(Jwt jwt) {
        return Mono.fromCallable(() -> {
            log.info("AuthService - verifyTokenFields() for sub: {}", jwt.getSubject());
            UserDTO user = new UserDTO();
            
            
            //Recupera la mappa "organization" dal token
            Map<String, Object> organizationMap = jwt.getClaim("organization");

            if (organizationMap == null) {
                log.warn("AuthService - Validazione token fallita per sub: {}", jwt.getSubject());
                throw new RuntimeException("Token incompleto: organization claim mancante");
            }

            OrganizationDTO org = objectMapper.convertValue(organizationMap, OrganizationDTO.class);

            user.setName(jwt.getClaimAsString("name"));
            user.setFamilyName(jwt.getClaimAsString("family_name"));
            user.setEmail(jwt.getClaimAsString("email"));
            user.setUid(jwt.getClaimAsString("uid"));
            user.setOrganization(org);

            log.info("AuthService - Validazione token riuscita per sub: {}", jwt.getSubject());
            return user;
        }).doOnError(e -> log.error("Errore validazione token: {}", e.getMessage()));
    }
}