package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/** Raised when payment-core cannot find the requested payment attempt. */
public class PaymentAttemptNotFoundException extends RuntimeException {
    public PaymentAttemptNotFoundException() {
        super("Payment attempt not found.");
    }
}
