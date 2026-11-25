package it.gov.pagopa.emd.ar.backoffice.connector.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalResponseDTOV1;
import reactor.core.publisher.Mono;

public interface PaymentConnector {
    Mono<PaymentAttemptSearchResponseDTOV1> searchPaymentAttempts(String iun, String tppId, String cursor, int limit);

    Mono<PaymentAttemptResponseDTOV1> getPaymentAttempt(String tppId, String iun);

    /**
     * Creates a retrieval token for the given TPP by calling emd-payment
     * ({@code POST /emd/payment/retrievalTokens/{entityId}}).
     *
     * @param entityId         the identifier of the TPP the token is created for
     * @param retrievalRequest the retrieval configuration (agent, originId, optional linkVersion)
     * @return a {@link Mono} emitting the {@link RetrievalResponseDTOV1} returned by emd-payment;
     *         it errors with {@code ExternalServiceException} if the upstream call fails
     */
    Mono<RetrievalResponseDTOV1> saveRetrieval(String entityId, RetrievalRequestDTOV1 retrievalRequest);
}
