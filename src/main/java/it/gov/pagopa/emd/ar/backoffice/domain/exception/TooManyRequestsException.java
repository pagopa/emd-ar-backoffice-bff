package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/**
 * Thrown when the upstream service returns HTTP 429 (Too Many Requests).
 * This indicates that the system has reached its capacity limit.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String upstreamDetail) {
        super(upstreamDetail);
    }
}