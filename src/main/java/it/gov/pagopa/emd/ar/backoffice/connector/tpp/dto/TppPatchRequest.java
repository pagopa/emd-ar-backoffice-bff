package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;

/**
 * Connector-layer DTO for {@code PATCH /emd/tpp/{tppId}}.
 *
 * <p>Only non-null fields are serialized (via {@code @JsonInclude(NON_NULL)}) so that the
 * emd-tpp service treats missing fields as "no change" — consistent with PATCH semantics.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TppPatchRequest {

    private String entityId;
    private String idPsp;
    private String businessName;
    private String legalAddress;
    private String messageUrl;
    private String authenticationUrl;
    private AuthenticationType authenticationType;
    private Contact contact;
    private String pspDenomination;
    private HashMap<String, AgentLink> agentLinks;
    private Boolean isPaymentEnabled;
    private String messageTemplate;
    private List<String> whitelistRecipient;
}

