package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * API-layer V1 representation of a single TPP returned by the search endpoint
 * ({@code GET /emd/backoffice/api/v1/tpp/search}).
 *
 * <p>Mirrors the upstream {@code TppDTOWithoutTokenSection} but intentionally
 * <strong>omits</strong> the {@code whitelistRecipient} field (potentially
 * sensitive — not needed by the backoffice frontend).</p>
 *
 * <p>Fields like {@code idPsp}, {@code legalAddress}, {@code state},
 * {@code creationDate}, {@code lastUpdateDate} and {@code isPaymentEnabled}
 * are exposed because the backoffice list/detail view needs them.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppDTOWithoutTokenSectionV1 {

    private String tppId;
    private String clientId;
    private String entityId;
    private String idPsp;
    private String businessName;
    private String legalAddress;
    private String messageUrl;
    private String authenticationUrl;
    private AuthenticationTypeV1 authenticationType;
    private ContactV1 contact;
    private Boolean state;
    private OffsetDateTime creationDate;
    private OffsetDateTime lastUpdateDate;
    private String pspDenomination;
    private Map<String, AgentLinkV1> agentLinks;
    private Boolean isPaymentEnabled;
    private String messageTemplate;
    // whitelistRecipient intentionally omitted — potentially sensitive
}

