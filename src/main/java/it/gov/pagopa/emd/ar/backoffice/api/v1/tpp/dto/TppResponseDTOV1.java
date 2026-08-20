package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * API response DTO for {@code GET /tpp/{entityId}}, {@code POST /tpp/{entityId}}
 * and {@code PATCH /tpp/{entityId}}.
 *
 * <p>The "base" fields are always populated. The "detailed-only" fields (annotated with
 * {@code @JsonInclude(NON_NULL)}) are only populated when the endpoint is called with
 * {@code detailed=true}; otherwise they are omitted from the JSON response.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppResponseDTOV1 {

    // ── Base fields (always populated) ───────────────────────────────────────
    private String tppId;
    private String businessName;
    private String messageUrl;
    private String authenticationUrl;
    private AuthenticationTypeV1 authenticationType;
    private ContactV1 contact;
    private String pspDenomination;
    private Map<String, AgentLinkV1> agentLinks;

    // ── Detailed-only fields (populated only when detailed=true) ─────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String entityId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clientId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String idPsp;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String legalAddress;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean state;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private OffsetDateTime creationDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private OffsetDateTime lastUpdateDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isPaymentEnabled;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String messageTemplate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> whitelistRecipient;
}
