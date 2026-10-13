package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cursor-paginated payment-attempt search response returned by payment-core and exposed by the BFF. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAttemptSearchResponseDTOV1 {
	/** Payments matching the search criteria on the current page. */
	private List<PaymentAttemptSearchItemDTOV1> content;

	/** Effective page size as reported by payment-core. */
	private Integer pageSize;

	/** Number of matching payment_attempt documents, as reported by payment-core. */
	private Long totalElements;

	/** Number of pages calculated by payment-core for the matching documents. */
	private Integer totalPages;

	/** Cursor to pass in the next request when another page is available. */
	private String nextCursor;

	/** Whether another page is available. */
	private Boolean hasNext;
}
