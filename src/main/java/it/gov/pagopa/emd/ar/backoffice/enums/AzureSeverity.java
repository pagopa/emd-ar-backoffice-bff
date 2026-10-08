package it.gov.pagopa.emd.ar.backoffice.enums;

import lombok.Getter;

@Getter
public enum AzureSeverity {
    DEBUG(0),
    INFO(1),
    WARNING(2),
    ERROR(3),
    CRITICAL(4),
    UNKNOWN(-1);

    private final int code;

    AzureSeverity(int code) {
        this.code = code;
    }

    // Metodo statico per convertire il numero di Azure nell'Enum corrispondente
    public static AzureSeverity fromCode(int code) {
        for (AzureSeverity severity : values()) {
            if (severity.getCode() == code) {
                return severity;
            }
        }
        return UNKNOWN;
    }
}