package it.gov.pagopa.emd.ar.backoffice.service.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptDetailDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalRequestDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.RetrievalResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.payment.PaymentConnector;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.PaymentAttemptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentConnector connector;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(connector);
    }

    @Test
    void getPaymentAttemptForwardsIdentifiersAndReturnsConnectorResponse() {
        PaymentAttemptResponseDTOV1 response = PaymentAttemptResponseDTOV1.builder()
                .originId("IUN-123")
                .tppId("tpp-456")
                .attemptDetails(List.of(PaymentAttemptDetailDTOV1.builder()
                        .noticeNumber("302000000000000000")
                        .paymentAttemptDate("2026-10-13T10:15:30.000+00:00")
                        .fiscalCode("RSSMRA80A01H501U")
                        .amount("1000")
                        .build()))
                .build();
        when(connector.getPaymentAttempt("tpp-456", "IUN-123")).thenReturn(Mono.just(response));

        StepVerifier.create(service.getPaymentAttempt("tpp-456", "IUN-123"))
                .assertNext(actual -> assertThat(actual).isSameAs(response))
                .verifyComplete();

        verify(connector).getPaymentAttempt("tpp-456", "IUN-123");
    }

    @Test
    void getPaymentAttemptPropagatesConnectorError() {
        PaymentAttemptNotFoundException notFound = new PaymentAttemptNotFoundException();
        when(connector.getPaymentAttempt("tpp-456", "IUN-missing")).thenReturn(Mono.error(notFound));

        StepVerifier.create(service.getPaymentAttempt("tpp-456", "IUN-missing"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(notFound))
                .verify();

        verify(connector).getPaymentAttempt("tpp-456", "IUN-missing");
    }

    @Test
    void saveRetrievalForwardsEntityIdAndRequestAndReturnsConnectorResponse() {
        RetrievalRequestDTOV1 request = RetrievalRequestDTOV1.builder()
                .agent("agent-1").originId("IUN-123").linkVersion("v1").build();
        RetrievalResponseDTOV1 response = RetrievalResponseDTOV1.builder()
                .retrievalId("retrieval-1").originId("IUN-123").tppId("tpp-456").build();
        when(connector.saveRetrieval("12345678901", request)).thenReturn(Mono.just(response));

        StepVerifier.create(service.saveRetrieval("12345678901", request))
                .assertNext(actual -> assertThat(actual).isSameAs(response))
                .verifyComplete();

        verify(connector).saveRetrieval("12345678901", request);
    }

    @Test
    void saveRetrievalPropagatesConnectorError() {
        RetrievalRequestDTOV1 request = RetrievalRequestDTOV1.builder()
                .agent("agent-1").originId("IUN-123").build();
        ExternalServiceException failure = new ExternalServiceException("PAYMENT_CORE", "saveRetrieval", "core error");
        when(connector.saveRetrieval("12345678901", request)).thenReturn(Mono.error(failure));

        StepVerifier.create(service.saveRetrieval("12345678901", request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(connector).saveRetrieval("12345678901", request);
    }
}
