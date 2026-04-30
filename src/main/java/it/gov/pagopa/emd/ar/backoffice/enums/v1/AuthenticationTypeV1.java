package it.gov.pagopa.emd.ar.backoffice.enums.v1;

import lombok.Getter;

/**
 * Enumeration defining the authentication mechanisms for TPP.
 */
@Getter
public enum AuthenticationTypeV1 {
    OAUTH2("OAUTH2");

    private final String type;

    AuthenticationTypeV1(String type) { this.type = type; }
}
