package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakRoleDTOV1 {
    /**
     * UUID interno del ruolo
     */
    @NotBlank
    private String id;
    @NotBlank
    private String name;
    private String description;
}