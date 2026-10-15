package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAttemptSearchItemDTOV1 {
    private String iun;
    private String tppId;
    private String businessName;
    private String entityId;
    private Integer attemptsCount;
}
