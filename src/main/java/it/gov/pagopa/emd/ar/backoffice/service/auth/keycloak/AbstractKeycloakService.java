package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

/**
 * Non-Spring base class providing shared URI-building and error-mapping utilities
 * for all Keycloak sub-services. Concrete Spring beans extend this class and pass
 * the configuration values via their own constructor injection.
 */
public abstract class AbstractKeycloakService {

    protected final String authServerUrl;
    protected final String realm;
    protected final ObjectMapper objectMapper;

    protected AbstractKeycloakService(String authServerUrl, String realm, ObjectMapper objectMapper) {
        this.authServerUrl = authServerUrl;
        this.realm = realm;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds a fully qualified URI for a Keycloak Admin REST path.
     * The first URI template variable is always {@code realm}; additional variables
     * can be appended via {@code extraVars}.
     *
     * @param path      relative path starting with {@code /} (may contain {@code {realm}} placeholder)
     * @param extraVars additional URI template variables after realm
     * @return resolved {@link URI}
     */
    protected URI adminUri(String path, Object... extraVars) {
        Object[] allVars = new Object[1 + extraVars.length];
        allVars[0] = realm;
        System.arraycopy(extraVars, 0, allVars, 1, extraVars.length);
        return UriComponentsBuilder.fromUriString(authServerUrl)
                .path("/admin/realms/{realm}" + path)
                .buildAndExpand(allVars)
                .toUri();
    }

    /**
     * Builds the token endpoint URI for the configured realm.
     */
    protected URI tokenUri() {
        return UriComponentsBuilder.fromUriString(authServerUrl)
                .path("/realms/{realm}/protocol/openid-connect/token")
                .buildAndExpand(realm)
                .toUri();
    }

    /**
     * Maps a Keycloak error response body to an {@link ExternalServiceException}.
     * Attempts JSON parsing to extract {@code error_description}; falls back to the raw body.
     *
     * @param operation label for the failing Keycloak operation (used in the exception message)
     * @param errorBody raw HTTP response body from Keycloak
     */
    protected Mono<Throwable> handleKeycloakError(String operation, String errorBody) {
        try {
            Map<String, Object> errorMap = objectMapper.readValue(errorBody, new TypeReference<>() {});
            String description = (String) errorMap.getOrDefault("error_description", errorMap.get("error"));
            return Mono.error(new ExternalServiceException("KEYCLOAK", operation,
                    description != null ? description : errorBody));
        } catch (Exception e) {
            return Mono.error(new ExternalServiceException("KEYCLOAK", operation, errorBody));
        }
    }
}

