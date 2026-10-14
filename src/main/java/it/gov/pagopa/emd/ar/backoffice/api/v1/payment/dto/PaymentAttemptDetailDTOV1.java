package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Details of one payment attempt. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAttemptDetailDTOV1 {
    private String noticeNumber;
    private String paymentAttemptDate;
    private String fiscalCode;
    private String amount;
}
