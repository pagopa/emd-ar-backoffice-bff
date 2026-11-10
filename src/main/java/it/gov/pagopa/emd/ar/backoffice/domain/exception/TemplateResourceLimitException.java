package it.gov.pagopa.emd.ar.backoffice.domain.exception;

/**
 * Segnala che il rendering di un template FreeMarker ha superato uno dei limiti di
 * contenimento delle risorse (dimensione input, dimensione output o budget di
 * esecuzione/tempo).
 *
 * <p>Estende {@link InvalidTemplateException} così da ereditare la mappatura a
 * HTTP 400 nel {@code ControllerExceptionHandler}: il superamento di un limite è
 * trattato come input non valido fornito dal client.</p>
 */
public class TemplateResourceLimitException extends InvalidTemplateException {

    public TemplateResourceLimitException(String message) {
        super(message);
    }

    public TemplateResourceLimitException(String message, Throwable cause) {
        super(message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
