package it.gov.pagopa.emd.ar.backoffice.dto.v1;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import it.gov.pagopa.emd.ar.backoffice.enums.v1.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.model.v1.AgentLinkV1;
import it.gov.pagopa.emd.ar.backoffice.model.v1.ContactV1;
import it.gov.pagopa.emd.ar.backoffice.model.v1.TokenSectionV1;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Data Transfer Object representing a complete TPP entity with full configuration.
 */ 
@Data
@SuperBuilder
@NoArgsConstructor
public class TppDTOV1 {

    private String tppId;

    @NotBlank(message = "Entity ID must not be blank")
    @Pattern(regexp = "^(\\d{11}|[A-Za-z0-9]{16})$", message = "Entity ID must be 11 digits or up to 16 alphanumeric characters")
    private String entityId;

    @NotBlank(message = "ID PSP must not be blank")
    private String idPsp;

    @NotBlank(message = "Business name must not be blank")
    private String businessName;

    private String legalAddress;

    @Pattern(regexp = "^(https?|ftp)://[^ /$.?#].[^ ]*$", message = "Message URL must be a valid URL")
    private String messageUrl;

    @Pattern(regexp = "^(https?|ftp)://[^ /$.?#].[^ ]*$", message = "Authentication URL must be a valid URL")
    private String authenticationUrl;

    @NotNull(message = "Authentication type must not be null")
    private AuthenticationTypeV1 authenticationType;

    private ContactV1 contact;

    private Boolean state;
    
    private LocalDateTime creationDate;
    private LocalDateTime lastUpdateDate;

    private TokenSectionV1 tokenSection;

    private String pspDenomination;

    @NotNull(message = "Agent Link must not be null")
    private HashMap<String, AgentLinkV1> agentLinks;

    private Boolean isPaymentEnabled;

    private String messageTemplate;

    private List<String> whitelistRecipient;
    
}