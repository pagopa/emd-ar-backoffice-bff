package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto;

public record AdminAuthRequestV1(
    String code,
    //PKCE
    String codeVerifier,
    String state
) {}