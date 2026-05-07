package it.gov.pagopa.emd.ar.backoffice.domain.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

@Data
public class Organization {
    private String id;
    private String name;
    private List<Role> roles;
    @JsonAlias("fiscal_code")
    private String fiscalCode;
    private String ipaCode;
}
