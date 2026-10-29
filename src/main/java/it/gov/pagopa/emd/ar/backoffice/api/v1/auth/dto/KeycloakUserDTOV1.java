package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakUserDTOV1 {
    
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private Boolean emailVerified;
    private Boolean enabled;
    private Long createdTimestamp;
    
    // In Keycloak, custom attributes are always lists of strings,
    // even if they contain only a single value.
    private Map<String, List<String>> attributes;

    private List<String> realmRoles;
    private Map<String, List<String>> clientRoles;
}