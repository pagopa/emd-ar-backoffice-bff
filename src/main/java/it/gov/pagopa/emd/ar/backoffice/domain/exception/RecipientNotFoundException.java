package it.gov.pagopa.emd.ar.backoffice.domain.exception;

public class RecipientNotFoundException extends RuntimeException {
    public RecipientNotFoundException(String message) {
        super(message);
    }
}