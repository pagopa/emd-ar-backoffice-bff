package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@RequestMapping("/emd/backoffice/api/v1")
public interface PaymentAttemptControllerV1 {

    /**
     * Searches payment attempts through payment-core using cursor-based pagination.
     *
     * <p>The response preserves the core's {@code content}, {@code pageSize}, {@code totalElements},
     * {@code totalPages}, {@code nextCursor} and {@code hasNext} values. {@code totalElements}
     * counts matching payment-attempt documents; each content item's {@code attemptsCount} is a
     * separate per-payment value.</p>
     */
    @GetMapping(value = "payment-core/attempts/search", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<PaymentAttemptSearchResponseDTOV1>> searchPaymentAttempts(
            @RequestParam(name = "iun", required = false) String iun,
            @RequestParam(name = "tppId", required = false) String tppId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit);
}
