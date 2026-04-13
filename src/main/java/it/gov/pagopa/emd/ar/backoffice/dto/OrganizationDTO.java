package it.gov.pagopa.emd.ar.backoffice.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class OrganizationDTO {
    private String id;
    private String name;
    private List<RoleDTO> roles;
    @JsonProperty("fiscal_code")
    private String fiscalCode;
    private String ipaCode;
}
