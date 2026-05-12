package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

/**
 * Full connector-layer representation of the response from the emd-tpp service
 * when querying a TPP by {@code entityId} ({@code GET /emd/tpp/entityId/{entityId}}).
 *
 * <p>Maps the upstream {@code TppDTOWithoutTokenSection} payload. Unknown fields are
 * silently ignored via {@link JsonIgnoreProperties} so future upstream additions do not
 * break deserialization.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TppEntityIdResponse {
    private String tppId;
    private String entityId;
    private String idPsp;
    private String businessName;
    private String legalAddress;
    private String messageUrl;
    private String authenticationUrl;
    private AuthenticationType authenticationType;
    private Contact contact;
    private Boolean state;
    private LocalDateTime creationDate;
    private LocalDateTime lastUpdateDate;
    private String pspDenomination;
    private HashMap<String, AgentLink> agentLinks;
    private Boolean isPaymentEnabled;
    private String messageTemplate;
    private List<String> whitelistRecipient;
}

