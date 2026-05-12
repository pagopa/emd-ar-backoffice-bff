package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API response DTO for {@code GET /tpp/{entityId}}.
 *
 * <p>Mirrors the upstream {@code TppDTOWithoutTokenSection} from the emd-tpp service,
 * converted to API-layer types (V1 variants). Token-section data is NOT included.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppResponseDTOV1 {

    private String tppId;
    private String entityId;
    private String idPsp;
    private String businessName;
    private String legalAddress;
    private String messageUrl;
    private String authenticationUrl;
    private AuthenticationTypeV1 authenticationType;
    private ContactV1 contact;
    private Boolean state;
    private LocalDateTime creationDate;
    private LocalDateTime lastUpdateDate;
    private String pspDenomination;
    private Map<String, AgentLinkV1> agentLinks;
    private Boolean isPaymentEnabled;
    private String messageTemplate;
    private List<String> whitelistRecipient;
}

