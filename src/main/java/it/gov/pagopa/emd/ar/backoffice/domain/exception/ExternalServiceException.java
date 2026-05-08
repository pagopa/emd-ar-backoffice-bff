package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/**
 * Thrown when a call to an external service (e.g. Keycloak, emd-tpp) fails.
 * Maps to HTTP 502 Bad Gateway in {@link ControllerExceptionHandler}.
 */
public class ExternalServiceException extends RuntimeException {

    private final int httpStatusCode;

    public ExternalServiceException(String service, String operation, String detail) {
        this(service, operation, detail, 502);
    }

    public ExternalServiceException(String service, String operation, String detail, int httpStatusCode) {
        super("[" + service + "][" + operation + "] " + detail);
        this.httpStatusCode = httpStatusCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }
}

