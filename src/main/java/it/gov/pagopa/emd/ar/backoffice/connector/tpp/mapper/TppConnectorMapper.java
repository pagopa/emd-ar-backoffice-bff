package it.gov.pagopa.emd.ar.backoffice.connector.tpp.mapper;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.TokenSectionV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.VersionDetailsV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.AgentLink;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.AuthenticationType;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.Contact;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.VersionDetails;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the API {@link TppDTOV1} to the outbound connector request {@link TppCreateRequest},
 * applying required default values and converting API-layer types (V1, with Jakarta constraints)
 * to connector-layer types (no Jakarta), so the two layers remain fully independent.
 * <p>
 * TODO: replace placeholder default values once the full onboarding flow is implemented.
 * </p>
 */
public final class TppConnectorMapper {

    // ── Temporary defaults ────────────────────────────────────────────────────
    private static final String DEFAULT_ID_PSP         = "Temporary PSP ID";
    private static final String DEFAULT_LEGAL_ADDRESS  = "Temporary Address";
    private static final String DEFAULT_CONTACT_NAME   = "Temporary Contact";
    private static final String DEFAULT_CONTACT_NUMBER = "1234567890";
    private static final String DEFAULT_CONTACT_EMAIL  = "test@example.com";

    private TppConnectorMapper() {}

    public static TppCreateRequest toCreateRequest(TppDTOV1 input) {
        return TppCreateRequest.builder()
                .entityId(input.getEntityId())
                .businessName(input.getBusinessName())
                .authenticationType(toAuthenticationType(input.getAuthenticationType()))
                .agentLinks(toAgentLinks(input.getAgentLinks()))
                .messageUrl(input.getMessageUrl())
                .authenticationUrl(input.getAuthenticationUrl())
                .tokenSection(toTokenSection(input.getTokenSection()))
                .messageTemplate(input.getMessageTemplate())
                .whitelistRecipient(input.getWhitelistRecipient())
                .idPsp(DEFAULT_ID_PSP)
                .pspDenomination(input.getBusinessName())
                .legalAddress(DEFAULT_LEGAL_ADDRESS)
                .contact(Contact.builder()
                        .name(DEFAULT_CONTACT_NAME)
                        .number(DEFAULT_CONTACT_NUMBER)
                        .email(DEFAULT_CONTACT_EMAIL)
                        .build())
                .state(false)
                .isPaymentEnabled(false)
                .build();
    }

    private static AuthenticationType toAuthenticationType(AuthenticationTypeV1 v1) {
        if (v1 == null) return null;
        return AuthenticationType.valueOf(v1.name());
    }

    private static TokenSection toTokenSection(TokenSectionV1 v1) {
        if (v1 == null) return null;
        return TokenSection.builder()
                .contentType(v1.getContentType())
                .pathAdditionalProperties(v1.getPathAdditionalProperties())
                .bodyAdditionalProperties(v1.getBodyAdditionalProperties())
                .build();
    }

    private static Map<String, AgentLink> toAgentLinks(Map<String, AgentLinkV1> v1Map) {
        if (v1Map == null) return null;
        return v1Map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toAgentLink(e.getValue())));
    }

    private static AgentLink toAgentLink(AgentLinkV1 v1) {
        if (v1 == null) return null;
        Map<String, VersionDetails> versions = null;
        if (v1.getVersions() != null) {
            versions = v1.getVersions().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> toVersionDetails(e.getValue())));
        }
        return AgentLink.builder()
                .fallBackLink(v1.getFallBackLink())
                .versions(versions)
                .build();
    }

    private static VersionDetails toVersionDetails(VersionDetailsV1 v1) {
        if (v1 == null) return null;
        return VersionDetails.builder().link(v1.getLink()).build();
    }
}
