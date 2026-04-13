package it.gov.pagopa.emd.ar.backoffice.dto;

import java.util.List;

import lombok.Data;

@Data
public class OrganizationDTO {
    private String id;
    private String name;
    private List<RoleDTO> roles;
    private String fiscalCode;
    private String ipaCode;
}
