package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.TokenSectionV1;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Payload sent to the remote emd-tpp service when creating a new TPP.
 * <p>
 * Deliberately separate from {@code TppDTOV1} (the API input) so that:
 * <ul>
 *   <li>Jakarta validation constraints on the API DTO do not leak into the outbound request.</li>
 *   <li>The downstream contract can evolve independently of the API contract.</li>
 * </ul>
 * </p>
 */
@Value
@Builder
public class TppCreateRequest {

    String entityId;
    String businessName;
    String idPsp;
    String pspDenomination;
    String legalAddress;
    String messageUrl;
    String authenticationUrl;
    AuthenticationTypeV1 authenticationType;
    ContactV1 contact;
    Boolean state;
    TokenSectionV1 tokenSection;
    Map<String, AgentLinkV1> agentLinks;
    Boolean isPaymentEnabled;
    String messageTemplate;
    List<String> whitelistRecipient;
}

