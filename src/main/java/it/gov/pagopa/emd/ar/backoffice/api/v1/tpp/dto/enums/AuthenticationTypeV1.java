package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums;

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
