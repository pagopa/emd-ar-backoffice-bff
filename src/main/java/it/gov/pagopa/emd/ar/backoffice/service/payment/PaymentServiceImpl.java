package it.gov.pagopa.emd.ar.backoffice.service.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.payment.PaymentConnector;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentConnector paymentAttemptConnector;

    public PaymentServiceImpl(PaymentConnector paymentAttemptConnector) {
        this.paymentAttemptConnector = paymentAttemptConnector;
    }

    @Override
    public Mono<PaymentAttemptSearchResponseDTOV1> searchPaymentAttempts(
            String iun, String tppId, String cursor, int limit) {
        return paymentAttemptConnector.searchPaymentAttempts(iun, tppId, cursor, limit)
                .doOnError(error -> log.warn("[AR-BFF][PAYMENT_ATTEMPT_SEARCH] Search failed: {}",
                        error.getClass().getSimpleName()));
    }

    @Override
    public Mono<PaymentAttemptResponseDTOV1> getPaymentAttempt(String tppId, String iun) {
        return paymentAttemptConnector.getPaymentAttempt(tppId, iun)
                .doOnError(error -> log.warn("[AR-BFF][PAYMENT_ATTEMPT_GET] Get failed: {}",
                        error.getClass().getSimpleName()));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<RetrievalResponseDTOV1> saveRetrieval(String entityId, RetrievalRequestDTOV1 retrievalRequest) {
        log.info("[AR-BFF][RETRIEVAL_TOKEN_CREATE] Creating retrieval token for entityId={}", entityId);
        return paymentAttemptConnector.saveRetrieval(entityId, retrievalRequest)
                .doOnSuccess(r -> log.info("[AR-BFF][RETRIEVAL_TOKEN_CREATE] Retrieval token created for entityId={}", entityId))
                .doOnError(error -> log.warn("[AR-BFF][RETRIEVAL_TOKEN_CREATE] Creation failed: {}",
                        error.getClass().getSimpleName()));
    }

}
