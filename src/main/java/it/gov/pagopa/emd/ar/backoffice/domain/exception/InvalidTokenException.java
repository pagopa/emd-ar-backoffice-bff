package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/**
 * Thrown when an incoming token is missing, malformed, or fails validation.
 * Maps to HTTP 401 Unauthorized in {@link ControllerExceptionHandler}.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String detail) {
        super(detail);
    }
}

