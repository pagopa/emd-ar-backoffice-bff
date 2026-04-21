package it.gov.pagopa.emd.ar.backoffice.dto.v1;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

@Data
public class OrganizationDTOV1 {
    private String id;
    private String name;
    private List<RoleDTOV1> roles;
    @JsonAlias("fiscal_code")
    private String fiscalCode;
    private String ipaCode;
}
