package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Single payment-attempt response returned by payment-core. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAttemptResponseDTOV1 {
    private String originId;
    private String tppId;
    private List<PaymentAttemptDetailDTOV1> attemptDetails;
}
