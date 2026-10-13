package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.payment.PaymentAttemptConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAttemptConnectorSearchTest {

    private static final String BASE_URL = "http://emd-payment.test";
    private static final String EMPTY_RESPONSE = """
            {"content":[],"pageSize":20,"totalElements":0,"totalPages":0,"nextCursor":null,"hasNext":false}
            """;

    private PaymentAttemptConnectorImpl connectorWith(ExchangeFunction exchangeFunction) {
        return new PaymentAttemptConnectorImpl(WebClient.builder().exchangeFunction(exchangeFunction), BASE_URL);
    }

    private ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    @Test
    void searchForwardsBothFiltersAndReturnsCursorMetadata() {
        String body = """
                {"content":[{"iun":"IUN-123","tppId":"tpp-456","attemptsCount":2}],
                 "pageSize":20,"totalElements":135,"totalPages":7,
                 "nextCursor":"507f1f77bcf86cd799439011","hasNext":true}
                """;
        String[] capturedUrl = new String[1];
        var connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(jsonResponse(HttpStatus.OK, body));
        });

        StepVerifier.create(connector.searchPaymentAttempts("IUN-123", "tpp-456", null, 20))
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().getFirst().getTppId()).isEqualTo("tpp-456");
                    assertThat(response.getContent().getFirst().getAttemptsCount()).isEqualTo(2);
                    assertThat(response.getPageSize()).isEqualTo(20);
                    // The core's document count is forwarded as-is, not calculated from attemptsCount.
                    assertThat(response.getTotalElements()).isEqualTo(135L);
                    assertThat(response.getTotalPages()).isEqualTo(7);
                    assertThat(response.getHasNext()).isTrue();
                    assertThat(response.getNextCursor()).isEqualTo("507f1f77bcf86cd799439011");
                })
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("/emd/payment/attempts/search", "iun=IUN-123", "tppId=tpp-456", "limit=20");
        assertThat(capturedUrl[0]).doesNotContain("psp", "offset", "page=");
    }

    @Test
    void searchForwardsOnlyIun() {
        String[] capturedUrl = new String[1];
        var connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(jsonResponse(HttpStatus.OK, EMPTY_RESPONSE));
        });

        StepVerifier.create(connector.searchPaymentAttempts("IUN-123", null, null, 20))
                .assertNext(response -> {
                    assertThat(response.getContent()).isEmpty();
                    assertThat(response.getPageSize()).isEqualTo(20);
                    assertThat(response.getTotalElements()).isZero();
                    assertThat(response.getTotalPages()).isZero();
                    assertThat(response.getHasNext()).isFalse();
                })
                .verifyComplete();
        assertThat(capturedUrl[0]).contains("iun=IUN-123").doesNotContain("tppId=");
    }

    @Test
    void searchForwardsOnlyTppId() {
        String[] capturedUrl = new String[1];
        var connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(jsonResponse(HttpStatus.OK, EMPTY_RESPONSE));
        });

        StepVerifier.create(connector.searchPaymentAttempts(null, "tpp-456", null, 20)).expectNextCount(1).verifyComplete();
        assertThat(capturedUrl[0]).contains("tppId=tpp-456").doesNotContain("iun=");
    }

    @Test
    void searchForwardsCursorAndLimitForNextPage() {
        String cursor = "507f1f77bcf86cd799439011";
        String[] capturedUrl = new String[1];
        var connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(jsonResponse(HttpStatus.OK, EMPTY_RESPONSE));
        });

        StepVerifier.create(connector.searchPaymentAttempts("IUN-123", null, cursor, 35)).expectNextCount(1).verifyComplete();
        assertThat(capturedUrl[0]).contains("cursor=" + cursor, "limit=35");
    }

    @Test
    void searchMapsCoreFailureToExternalServiceException() {
        var connector = connectorWith(request -> Mono.just(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "core error")));
        StepVerifier.create(connector.searchPaymentAttempts("IUN-123", null, null, 20))
                .expectError(ExternalServiceException.class)
                .verify();
    }
}
