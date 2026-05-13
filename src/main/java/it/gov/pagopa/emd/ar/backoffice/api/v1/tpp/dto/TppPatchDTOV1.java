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

/**
 * API request DTO for {@code PATCH /tpp/{entityId}}.
 *
 * <p>Only non-null fields present in the request body will be applied to the existing TPP.
 * All omitted or null fields are ignored by the backend and will retain their current
 * values in the database.</p>
 *
 * <p>Intentionally limited: {@code entityId} (injected by APIM via path variable),
 * {@code idPsp}, {@code legalAddress}, {@code isPaymentEnabled}, {@code messageTemplate}
 * and {@code whitelistRecipient} are NOT exposed — they will be added in a future iteration.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppPatchDTOV1 {

    private String businessName;

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
}
