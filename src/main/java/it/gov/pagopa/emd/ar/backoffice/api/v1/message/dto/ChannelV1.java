package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import lombok.Getter;

/**
 * Enum representing the communication channels available for sending messages.
 */
@Getter 
public enum ChannelV1 {

        SEND("SEND");

    private final String status;

    ChannelV1(String status) {
        this.status = status;
    }

}