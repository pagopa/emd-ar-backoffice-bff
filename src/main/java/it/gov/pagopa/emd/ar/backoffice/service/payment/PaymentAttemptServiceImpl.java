package it.gov.pagopa.emd.ar.backoffice.service.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.payment.PaymentAttemptConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class PaymentAttemptServiceImpl implements PaymentAttemptService {

    private final PaymentAttemptConnector paymentAttemptConnector;

    public PaymentAttemptServiceImpl(PaymentAttemptConnector paymentAttemptConnector) {
        this.paymentAttemptConnector = paymentAttemptConnector;
    }

    @Override
    public Mono<PaymentAttemptSearchResponseDTOV1> searchPaymentAttempts(
            String iun, String tppId, String cursor, int limit) {
        return paymentAttemptConnector.searchPaymentAttempts(iun, tppId, cursor, limit)
                .doOnError(error -> log.warn("[AR-BFF][PAYMENT_ATTEMPT_SEARCH] Search failed: {}",
                        error.getClass().getSimpleName()));
    }
}
