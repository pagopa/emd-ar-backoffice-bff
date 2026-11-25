package it.gov.pagopa.emd.ar.backoffice.service.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalResponseDTOV1;
import reactor.core.publisher.Mono;

public interface PaymentService {
    Mono<PaymentAttemptSearchResponseDTOV1> searchPaymentAttempts(String iun, String tppId, String cursor, int limit);

    Mono<PaymentAttemptResponseDTOV1> getPaymentAttempt(String tppId, String iun);

    /**
     * Creates a retrieval token for the given TPP, delegating to the payment connector.
     *
     * @param entityId         the identifier of the TPP the token is created for
     * @param retrievalRequest the retrieval configuration (agent, originId, optional linkVersion)
     * @return a {@link Mono} emitting the created retrieval ({@link RetrievalResponseDTOV1});
     *         errors from emd-payment are propagated as {@code ExternalServiceException}
     */
    Mono<RetrievalResponseDTOV1> saveRetrieval(String entityId, RetrievalRequestDTOV1 retrievalRequest);
}
