package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/**
 * Thrown when the upstream emd-tpp service rejects a {@code fields} query parameter
 * value with HTTP 400 and error code {@code INVALID_SEARCH_FIELD}.
 *
 * <p>This exception propagates a client-caused error back to the frontend as HTTP 400,
 * following the same pattern used for other 4xx domain exceptions
 * ({@link ResourceNotFoundException}, {@link TppAlreadyOnboardedException}, etc.).</p>
 */
public class InvalidSearchFieldException extends RuntimeException {

    private final String field;

    public InvalidSearchFieldException(String field, String upstreamDetail) {
        super("Invalid search field '%s': %s".formatted(field, upstreamDetail));
        this.field = field;
    }

    /** The invalid field name as sent upstream, if extractable; otherwise the raw upstream body. */
    public String getField() {
        return field;
    }
}

