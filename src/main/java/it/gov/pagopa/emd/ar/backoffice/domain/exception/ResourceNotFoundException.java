package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/**
 * Thrown when a required resource (e.g. a Keycloak group) cannot be found.
 * Maps to HTTP 404 Not Found in {@link ControllerExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, String key) {
        super(resource + " not found: " + key);
    }
}

