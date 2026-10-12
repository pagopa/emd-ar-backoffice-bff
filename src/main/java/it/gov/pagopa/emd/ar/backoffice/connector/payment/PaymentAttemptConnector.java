package it.gov.pagopa.emd.ar.backoffice.connector.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import reactor.core.publisher.Mono;

public interface PaymentAttemptConnector {
    Mono<PaymentAttemptSearchResponseDTOV1> searchPaymentAttempts(String iun, String tppId, String cursor, int limit);
}
