package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import lombok.Getter;

/**
 * Enum representing the states of a message in the notifier system.
 */
@Getter
public enum MessageStateV1 {

    IN_PROCESS("IN_PROCESS"),
    SENT("SENT"),
    ERROR("ERROR");

    private final String status;

    MessageStateV1(String status) {
        this.status = status;
    }

}
