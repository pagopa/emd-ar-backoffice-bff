package it.gov.pagopa.emd.ar.backoffice.connector.tpp.mapper;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOWithoutTokenSectionV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPatchDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.TokenSectionV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.VersionDetailsV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.AgentLink;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.AuthenticationType;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.Contact;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppPatchRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.VersionDetails;

import java.time.ZoneOffset;
import java.util.List;
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

    /**
     * Maps the API-layer {@link TppDTOV1} to a connector-layer {@link TppCreateRequest}.
     *
     * @param entityId the entity identifier injected by APIM (from JWT claim {@code orgFiscalCode})
     * @param input    the POST request body
     */
    public static TppCreateRequest toCreateRequest(String entityId, TppDTOV1 input) {
        return TppCreateRequest.builder()
                .entityId(entityId)
                .businessName(input.getBusinessName())
                .authenticationType(toAuthenticationType(input.getAuthenticationType()))
                .agentLinks(toAgentLinks(input.getAgentLinks()))
                .messageUrl(input.getMessageUrl())
                .authenticationUrl(input.getAuthenticationUrl())
                .tokenSection(toTokenSection(input.getTokenSection()))
                .idPsp(DEFAULT_ID_PSP)
                .pspDenomination(input.getPspDenomination() != null ? input.getPspDenomination() : input.getBusinessName())
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

    /**
     * Maps the API-layer {@link TppPatchDTOV1} to a connector-layer {@link TppPatchRequest}.
     * All fields may be null — the caller decides which fields to include.
     */
    public static TppPatchRequest toPatchRequest(TppPatchDTOV1 input) {
        if (input == null) return null;
        return TppPatchRequest.builder()
                .businessName(input.getBusinessName())
                .messageUrl(input.getMessageUrl())
                .authenticationUrl(input.getAuthenticationUrl())
                .authenticationType(toAuthenticationType(input.getAuthenticationType()))
                .contact(toContact(input.getContact()))
                .pspDenomination(input.getPspDenomination())
                .agentLinks(input.getAgentLinks() == null ? null
                        : new java.util.HashMap<>(toAgentLinks(input.getAgentLinks())))
                .build();
    }

    private static Contact toContact(it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1 v1) {
        if (v1 == null) return null;
        return Contact.builder()
                .name(v1.getName())
                .number(v1.getNumber())
                .email(v1.getEmail())
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

    // ── TppEntityIdResponse → TppResponseDTOV1 ───────────────────────────────

    /**
     * Maps a {@link TppEntityIdResponse} (connector layer) to the API-layer
     * {@link TppResponseDTOV1}, converting connector-layer enums and nested objects
     * to their V1 counterparts.
     *
     * <p>Only the "base" fields are populated; server-managed fields are omitted.
     * Use {@link #toTppResponseDTOV1Detailed(TppEntityIdResponse)} when all fields
     * are required (i.e. when {@code detailed=true}).</p>
     */
    public static TppResponseDTOV1 toTppResponseDTOV1(TppEntityIdResponse src) {
        return TppResponseDTOV1.builder()
                .tppId(src.getTppId())
                .businessName(src.getBusinessName())
                .messageUrl(src.getMessageUrl())
                .authenticationUrl(src.getAuthenticationUrl())
                .authenticationType(toAuthenticationTypeV1(src.getAuthenticationType()))
                .contact(toContactV1(src.getContact()))
                .pspDenomination(src.getPspDenomination())
                .agentLinks(toAgentLinksV1(src.getAgentLinks()))
                .build();
    }

    /**
     * Maps a {@link TppEntityIdResponse} (connector layer) to the API-layer
     * {@link TppResponseDTOV1} with <em>all</em> available fields populated,
     * including server-managed ones (entityId, idPsp, legalAddress, state, dates,
     * isPaymentEnabled, messageTemplate, whitelistRecipient, clientId).
     *
     * <p>Dates are converted from {@code LocalDateTime} to {@code OffsetDateTime} (UTC).</p>
     *
     * <p>Used when the caller requests {@code detailed=true}.</p>
     */
    public static TppResponseDTOV1 toTppResponseDTOV1Detailed(TppEntityIdResponse src) {
        return TppResponseDTOV1.builder()
                .tppId(src.getTppId())
                .businessName(src.getBusinessName())
                .messageUrl(src.getMessageUrl())
                .authenticationUrl(src.getAuthenticationUrl())
                .authenticationType(toAuthenticationTypeV1(src.getAuthenticationType()))
                .contact(toContactV1(src.getContact()))
                .pspDenomination(src.getPspDenomination())
                .agentLinks(toAgentLinksV1(src.getAgentLinks()))
                // detailed-only fields
                .entityId(src.getEntityId())
                .clientId(src.getClientId())
                .idPsp(src.getIdPsp())
                .legalAddress(src.getLegalAddress())
                .state(src.getState())
                .creationDate(src.getCreationDate() != null
                        ? src.getCreationDate().atOffset(ZoneOffset.UTC) : null)
                .lastUpdateDate(src.getLastUpdateDate() != null
                        ? src.getLastUpdateDate().atOffset(ZoneOffset.UTC) : null)
                .isPaymentEnabled(src.getIsPaymentEnabled())
                .messageTemplate(src.getMessageTemplate())
                .whitelistRecipient(src.getWhitelistRecipient())
                .build();
    }

    private static AuthenticationTypeV1 toAuthenticationTypeV1(AuthenticationType connector) {
        if (connector == null) return null;
        return AuthenticationTypeV1.valueOf(connector.name());
    }

    private static ContactV1 toContactV1(Contact connector) {
        if (connector == null) return null;
        return new ContactV1(connector.getName(), connector.getNumber(), connector.getEmail());
    }

    private static Map<String, AgentLinkV1> toAgentLinksV1(Map<String, AgentLink> connectorMap) {
        if (connectorMap == null) return null;
        return connectorMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toAgentLinkV1(e.getValue())));
    }

    private static AgentLinkV1 toAgentLinkV1(AgentLink connector) {
        if (connector == null) return null;
        Map<String, VersionDetailsV1> versions = null;
        if (connector.getVersions() != null) {
            versions = connector.getVersions().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> toVersionDetailsV1(e.getValue())));
        }
        return AgentLinkV1.builder()
                .fallBackLink(connector.getFallBackLink())
                .versions(versions)
                .build();
    }

    private static VersionDetailsV1 toVersionDetailsV1(VersionDetails connector) {
        if (connector == null) return null;
        return VersionDetailsV1.builder().link(connector.getLink()).build();
    }

    // ── TppSearchResponse → TppSearchResponseDTOV1 ───────────────────────────

    /**
     * Maps a {@link TppSearchResponse} (connector layer) to the API-layer
     * {@link TppSearchResponseDTOV1}, converting each content item via
     * {@link #toTppDTOWithoutTokenSectionV1(TppEntityIdResponse)}.
     */
    public static TppSearchResponseDTOV1 toTppSearchResponseDTOV1(TppSearchResponse src) {
        List<TppDTOWithoutTokenSectionV1> content = src.getContent() == null
                ? List.of()
                : src.getContent().stream()
                        .map(TppConnectorMapper::toTppDTOWithoutTokenSectionV1)
                        .collect(Collectors.toList());
        return TppSearchResponseDTOV1.builder()
                .content(content)
                .page(src.getPage())
                .size(src.getSize())
                .totalElements(src.getTotalElements())
                .totalPages(src.getTotalPages())
                .build();
    }

    /**
     * Maps a {@link TppEntityIdResponse} (connector layer) to the API-layer
     * {@link TppDTOWithoutTokenSectionV1}, converting dates from {@code LocalDateTime}
     * to {@code OffsetDateTime} (UTC) for the frontend.
     */
    public static TppDTOWithoutTokenSectionV1 toTppDTOWithoutTokenSectionV1(TppEntityIdResponse src) {
        return TppDTOWithoutTokenSectionV1.builder()
                .tppId(src.getTppId())
                .clientId(src.getClientId())
                .entityId(src.getEntityId())
                .idPsp(src.getIdPsp())
                .businessName(src.getBusinessName())
                .legalAddress(src.getLegalAddress())
                .messageUrl(src.getMessageUrl())
                .authenticationUrl(src.getAuthenticationUrl())
                .authenticationType(toAuthenticationTypeV1(src.getAuthenticationType()))
                .contact(toContactV1(src.getContact()))
                .state(src.getState())
                .creationDate(src.getCreationDate() != null
                        ? src.getCreationDate().atOffset(ZoneOffset.UTC) : null)
                .lastUpdateDate(src.getLastUpdateDate() != null
                        ? src.getLastUpdateDate().atOffset(ZoneOffset.UTC) : null)
                .pspDenomination(src.getPspDenomination())
                .agentLinks(toAgentLinksV1(src.getAgentLinks()))
                .isPaymentEnabled(src.getIsPaymentEnabled())
                .messageTemplate(src.getMessageTemplate())
                .whitelistRecipient(src.getWhitelistRecipient())
                .build();
    }
}
