package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO containing the PagoPA credentials (Keycloak OIDC client) for a given TPP.
 *
 * <p><strong>Privacy / masking:</strong> the {@code clientSecret} field MUST NOT appear
 * in any log statement. {@link #toString()} is intentionally overridden to mask the
 * secret, preventing accidental exposure via frameworks that call {@code toString()}
 * (e.g., Lombok-generated loggers, Spring error handlers, etc.).</p>
 *
 * <p>These are the PagoPA-specific credentials. Other credential types (e.g. SelfCare,
 * IO Platform) will be represented by distinct DTOs.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TppPagopaCredentialsDTOV1 {

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("clientSecret")
    private String clientSecret;

    /**
     * Overridden to prevent accidental {@code clientSecret} exposure in logs or error messages.
     */
    @Override
    public String toString() {
        return "TppPagopaCredentialsDTOV1{clientId='" + clientId + "', clientSecret='***MASKED***'}";
    }
}

