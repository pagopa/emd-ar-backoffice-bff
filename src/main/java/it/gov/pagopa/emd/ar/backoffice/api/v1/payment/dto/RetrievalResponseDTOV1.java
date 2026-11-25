package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Data Transfer Object representing the response for retrieval operations.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrievalResponseDTOV1 {
    private String retrievalId;
    /**
     * Notification address link
     */
    private String deeplink;
    private String pspDenomination;
    /**
     * Unique Numeric Identifier (IUN) of the notification
     */
    private String originId;
    private String tppId;
    private Boolean isPaymentEnabled;
    }