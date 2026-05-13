package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.TokenSectionV1;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Data Transfer Object representing the fields the frontend can supply when creating a new TPP.
 *
 * <p>Intentionally limited: server-managed fields ({@code tppId}, {@code state}, timestamps)
 * and temporarily-defaulted fields ({@code idPsp}, {@code legalAddress},
 * {@code isPaymentEnabled}, {@code messageTemplate}, {@code whitelistRecipient}) are NOT
 * exposed here. {@code entityId} is provided by the frontend in the request body (it
 * corresponds to the organisation fiscal code / VAT number).</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class TppDTOV1 {

    @NotBlank(message = "Entity ID must not be blank")
    private String entityId;

    @NotBlank(message = "Business name must not be blank")
    private String businessName;

    @Pattern(regexp = "^(https?|ftp)://[^ /$.?#].[^ ]*$", message = "Message URL must be a valid URL")
    private String messageUrl;

    @Pattern(regexp = "^(https?|ftp)://[^ /$.?#].[^ ]*$", message = "Authentication URL must be a valid URL")
    private String authenticationUrl;

    @NotNull(message = "Authentication type must not be null")
    private AuthenticationTypeV1 authenticationType;

    private ContactV1 contact;

    private TokenSectionV1 tokenSection;

    private String pspDenomination;

    @NotNull(message = "Agent Link must not be null")
    private Map<String, AgentLinkV1> agentLinks;
}
