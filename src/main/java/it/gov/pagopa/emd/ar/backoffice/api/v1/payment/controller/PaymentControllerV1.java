package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalResponseDTOV1;
import jakarta.validation.Valid;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptResponseDTOV1;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@RequestMapping("/emd/backoffice/api/v1")
public interface PaymentControllerV1 {

    /** Retrieves one payment attempt by its TPP and IUN identifiers. */
    @GetMapping(value = "payment-core/attempts/{tppId}/{iun}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<PaymentAttemptResponseDTOV1>> getPaymentAttempt(
            @PathVariable("tppId") String tppId,
            @PathVariable("iun") String iun);

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

    
    /**
     * Creates a new retrieval token for the specified TPP through payment-core.
     *
     * @param entityId            identifier of the TPP
     * @param retrievalRequestDTO the retrieval configuration ({@code agent} and {@code originId}
     *                            are required, {@code linkVersion} is optional)
     * @return a {@link Mono} containing a {@link ResponseEntity} with the
     *         {@link RetrievalResponseDTOV1} representing the outcome of the token creation;
     *         400 if the request body is invalid
     */
    @PostMapping("payment-core/retrievalTokens/{entityId}")
    Mono<ResponseEntity<RetrievalResponseDTOV1>> retrievalTokens(@Valid @PathVariable String entityId, @Valid @RequestBody RetrievalRequestDTOV1 retrievalRequestDTO);

}
