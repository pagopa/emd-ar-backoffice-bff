package it.gov.pagopa.emd.ar.backoffice.service.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import reactor.core.publisher.Mono;

public interface PaymentAttemptService {
    Mono<PaymentAttemptSearchResponseDTOV1> searchPaymentAttempts(String iun, String tppId, String cursor, int limit);
}
