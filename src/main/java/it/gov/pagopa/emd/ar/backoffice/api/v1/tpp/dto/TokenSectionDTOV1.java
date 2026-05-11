package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO containing the token-section credentials for a given TPP,
 * as stored on the emd-tpp service database.
 *
 * <p><strong>Privacy / masking:</strong> {@code pathAdditionalProperties} and
 * {@code bodyAdditionalProperties} may contain sensitive values (e.g. client secrets,
 * passwords, API keys). {@link #toString()} is intentionally overridden to mask both
 * maps, preventing accidental exposure in logs or error messages.</p>
 *
 * <p>These are the database-stored TPP credentials. PagoPA-specific Keycloak credentials
 * (client_id / client_secret) are handled by the separate
 * {@link TppPagopaCredentialsDTOV1}.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenSectionDTOV1 {

    @JsonProperty("contentType")
    private String contentType;

    @JsonProperty("pathAdditionalProperties")
    private Map<String, String> pathAdditionalProperties;

    @JsonProperty("bodyAdditionalProperties")
    private Map<String, String> bodyAdditionalProperties;

    /**
     * Overridden to prevent accidental exposure of credential values stored in the
     * additional-properties maps (e.g. client secrets, passwords).
     * Only the {@code contentType} and the property <em>keys</em> are logged.
     */
    @Override
    public String toString() {
        String pathKeys = pathAdditionalProperties != null
                ? pathAdditionalProperties.keySet().toString()
                : "null";
        String bodyKeys = bodyAdditionalProperties != null
                ? bodyAdditionalProperties.keySet().toString()
                : "null";
        return "TokenSectionDTOV1{contentType='" + contentType
                + "', pathAdditionalProperties.keys=" + pathKeys
                + ", bodyAdditionalProperties.keys=" + bodyKeys
                + ", values=***MASKED***}";
    }
}

