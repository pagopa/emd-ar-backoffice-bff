package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import lombok.Getter;

/**
 * Authentication mechanism as understood by the emd-tpp downstream service.
 * Deliberately separate from {@code AuthenticationTypeV1} (API layer) so that
 * the connector contract can evolve independently.
 */
@Getter
public enum AuthenticationType {
    OAUTH2("OAUTH2");

    private final String type;

    AuthenticationType(String type) { this.type = type; }
}

