package it.gov.pagopa.emd.ar.backoffice.connector.tpp.mapper;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;

/**
 * Maps the API {@link TppDTOV1} to the outbound connector request {@link TppCreateRequest},
 * applying the required default values for fields not supplied by the client.
 * <p>
 * Keeping this logic in the connector mapper ensures that:
 * <ul>
 *   <li>The service layer is pure orchestration (no field manipulation).</li>
 *   <li>Any change in the downstream contract (field added/removed/renamed)
 *       is contained here, without touching the API layer.</li>
 * </ul>
 * </p>
 * <p>
 * TODO: replace placeholder default values once the full onboarding flow is implemented.
 * </p>
 */
public final class TppConnectorMapper {

    // ── Temporary defaults — applied until the full onboarding flow provides real data ──
    private static final String DEFAULT_ID_PSP       = "Temporary PSP ID";
    private static final String DEFAULT_LEGAL_ADDRESS = "Temporary Address";
    private static final String DEFAULT_CONTACT_NAME  = "Temporary Contact";
    private static final String DEFAULT_CONTACT_NUMBER = "1234567890";
    private static final String DEFAULT_CONTACT_EMAIL  = "test@example.com";

    private TppConnectorMapper() {}

    /**
     * Converts an API input DTO into the connector request payload,
     * enriching it with required default values.
     *
     * @param input the validated API input DTO
     * @return a fully-populated {@link TppCreateRequest} ready to be serialized and sent
     */
    public static TppCreateRequest toCreateRequest(TppDTOV1 input) {
        return TppCreateRequest.builder()
                // ── fields forwarded from the API request ──────────────────
                .entityId(input.getEntityId())
                .businessName(input.getBusinessName())
                .authenticationType(input.getAuthenticationType())
                .agentLinks(input.getAgentLinks())
                .messageUrl(input.getMessageUrl())
                .authenticationUrl(input.getAuthenticationUrl())
                .tokenSection(input.getTokenSection())
                .messageTemplate(input.getMessageTemplate())
                .whitelistRecipient(input.getWhitelistRecipient())
                // ── computed / defaulted fields ────────────────────────────
                .idPsp(DEFAULT_ID_PSP)
                .pspDenomination(input.getBusinessName())
                .legalAddress(DEFAULT_LEGAL_ADDRESS)
                .contact(new ContactV1(DEFAULT_CONTACT_NAME, DEFAULT_CONTACT_NUMBER, DEFAULT_CONTACT_EMAIL))
                .state(false)
                .isPaymentEnabled(false)
                .build();
    }
}

