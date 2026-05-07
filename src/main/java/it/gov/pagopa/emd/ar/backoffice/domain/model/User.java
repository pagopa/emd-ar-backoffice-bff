package it.gov.pagopa.emd.ar.backoffice.domain.model;

import lombok.Data;

@Data
public class User {
    private String uid;
    private String name;
    private String familyName;
    private String email;
    private Organization organization;
}
