package it.gov.pagopa.emd.ar.backoffice.dto;

import lombok.Data;

@Data
public class UserDTO {
    private String uid;
    private String name;
    private String familyName;
    private String email;
    private OrganizationDTO organization;
}
