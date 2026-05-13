package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;

/**
 * API request DTO for {@code PATCH /tpp/{entityId}}.
 *
 * <p>Maps the upstream {@code TppDTOPatch}: only non-null fields present in the request
 * body will be applied to the existing TPP. All omitted or null fields are ignored by
 * the backend and will retain their current values in the database.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppPatchDTOV1 {

    @Pattern(regexp = "^(\\d{11}|[A-Za-z0-9]{16})$",
            message = "Entity ID must be 11 digits or up to 16 alphanumeric characters")
    private String entityId;

    private String idPsp;

    private String businessName;

    private String legalAddress;

    @Pattern(regexp = "^(https?|ftp)://[^ /$.?#].[^ ]*$",
            message = "Message URL must be a valid URL")
    private String messageUrl;

    @Pattern(regexp = "^(https?|ftp)://[^ /$.?#].[^ ]*$",
            message = "Authentication URL must be a valid URL")
    private String authenticationUrl;

    private AuthenticationTypeV1 authenticationType;

    private ContactV1 contact;

    private String pspDenomination;

    private HashMap<String, AgentLinkV1> agentLinks;

    private Boolean isPaymentEnabled;

    private String messageTemplate;

    private List<String> whitelistRecipient;
}

