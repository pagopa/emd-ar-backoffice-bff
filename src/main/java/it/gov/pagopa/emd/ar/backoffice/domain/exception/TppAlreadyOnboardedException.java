package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/**
 * Thrown when the TPP service rejects a save request because the TPP is already onboarded
 * (HTTP 409 Conflict with {@code TPP_ALREADY_ONBOARDED} error code).
 * Maps to HTTP 409 Conflict in the controller exception handler.
 */
public class TppAlreadyOnboardedException extends RuntimeException {

    public TppAlreadyOnboardedException(String message) {
        super(message);
    }
}

