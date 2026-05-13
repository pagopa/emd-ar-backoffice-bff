package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * API response DTO for {@code GET /tpp/{entityId}}, {@code POST /tpp/{entityId}}
 * and {@code PATCH /tpp/{entityId}}.
 *
 * <p>Contains only the fields exposed to the frontend. Server-managed fields
 * (entityId, idPsp, legalAddress, state, creationDate, lastUpdateDate,
 * isPaymentEnabled, messageTemplate, whitelistRecipient) are intentionally omitted.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppResponseDTOV1 {

    private String tppId;
    private String businessName;
    private String messageUrl;
    private String authenticationUrl;
    private AuthenticationTypeV1 authenticationType;
    private ContactV1 contact;
    private String pspDenomination;
    private Map<String, AgentLinkV1> agentLinks;
}

