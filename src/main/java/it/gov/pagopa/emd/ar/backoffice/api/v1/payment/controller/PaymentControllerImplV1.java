package it.gov.pagopa.emd.ar.backoffice.api.v1.payment.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.payment.PaymentService;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
public class PaymentControllerImplV1 implements PaymentControllerV1 {

    private static final String OBJECT_ID_CURSOR_PATTERN = "[0-9a-fA-F]{24}";
    private final PaymentService paymentService;

    public PaymentControllerImplV1(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public Mono<ResponseEntity<PaymentAttemptResponseDTOV1>> getPaymentAttempt(String tppId, String iun) {
        if (isBlank(tppId) || isBlank(iun)) {
            return Mono.error(new ValidationException("Both tppId and iun must be provided."));
        }
        return paymentService.getPaymentAttempt(tppId, iun).map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<PaymentAttemptSearchResponseDTOV1>> searchPaymentAttempts(
            String iun, String tppId, String cursor, int limit) {
        if (isBlank(iun) && isBlank(tppId)) {
            return Mono.error(new ValidationException("At least one of iun or tppId must be provided."));
        }
        if (limit < 1 || limit > 100) {
            return Mono.error(new ValidationException("limit must be between 1 and 100."));
        }
        if (cursor != null && !cursor.matches(OBJECT_ID_CURSOR_PATTERN)) {
            return Mono.error(new ValidationException("cursor must be a 24-character hexadecimal MongoDB ObjectId."));
        }

        log.info("[AR-BFF][PAYMENT_ATTEMPT_SEARCH] Request received (iunPresent={}, tppIdPresent={}, cursorPresent={}, limit={})",
                !isBlank(iun), !isBlank(tppId), cursor != null, limit);
        return paymentService.searchPaymentAttempts(iun, tppId, cursor, limit)
                .map(ResponseEntity::ok);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ResponseEntity<RetrievalResponseDTOV1>> retrievalTokens(String entityId, RetrievalRequestDTOV1 retrievalRequestDTO) {
        return paymentService.saveRetrieval(entityId, retrievalRequestDTO)
                .map(ResponseEntity::ok);
    }
}
