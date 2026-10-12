package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAttemptSearchResponseDTOV1 {
	private List<PaymentAttemptSearchItemDTOV1> payments;
	private Integer returnedCount;
	private Long attemptsCount;
	private String nextCursor;
	private Boolean hasMore;
}
