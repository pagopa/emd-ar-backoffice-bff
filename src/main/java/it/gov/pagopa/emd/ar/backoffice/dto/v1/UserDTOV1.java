package it.gov.pagopa.emd.ar.backoffice.dto.v1;

import lombok.Data;

@Data
public class UserDTOV1 {
    private String uid;
    private String name;
    private String familyName;
    private String email;
    private OrganizationDTOV1 organization;
}
