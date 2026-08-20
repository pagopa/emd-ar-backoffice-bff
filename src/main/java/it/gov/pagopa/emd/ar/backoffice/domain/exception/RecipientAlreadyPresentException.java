package it.gov.pagopa.emd.ar.backoffice.domain.exception;

public class RecipientAlreadyPresentException extends RuntimeException {
    public RecipientAlreadyPresentException(String message) {
        super(message);
    }
}