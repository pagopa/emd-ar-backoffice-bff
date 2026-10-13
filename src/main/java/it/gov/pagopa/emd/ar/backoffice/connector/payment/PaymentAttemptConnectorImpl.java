package it.gov.pagopa.emd.ar.backoffice.connector.payment;

import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@Service
public class PaymentAttemptConnectorImpl implements PaymentAttemptConnector {

    private static final String SEARCH_PATH = "/emd/payment/attempts/search";
    private final WebClient webClient;

    public PaymentAttemptConnectorImpl(WebClient.Builder webClientBuilder,
                                       @Value("${rest.client.payment.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Mono<PaymentAttemptSearchResponseDTOV1> searchPaymentAttempts(
            String iun, String tppId, String cursor, int limit) {
        return webClient.get()
                .uri(uriBuilder -> buildSearchUri(uriBuilder, iun, tppId, cursor, limit))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("Upstream request failed")
                                .flatMap(body -> Mono.error(new ExternalServiceException(
                                        "PAYMENT_CORE", "searchPaymentAttempts", body))))
                .bodyToMono(PaymentAttemptSearchResponseDTOV1.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .onErrorMap(error -> !(error instanceof ExternalServiceException),
                        error -> new ExternalServiceException("PAYMENT_CORE", "searchPaymentAttempts",
                                "Upstream request failed"))
                .doOnError(error -> log.warn("[PAYMENT-CORE-CONNECTOR] GET {} failed: {}",
                        SEARCH_PATH, error.getClass().getSimpleName()));
    }

    private URI buildSearchUri(UriBuilder uriBuilder, String iun, String tppId, String cursor, int limit) {
        uriBuilder.path(SEARCH_PATH).queryParam("limit", limit);
        if (iun != null && !iun.isBlank()) {
            uriBuilder.queryParam("iun", iun);
        }
        if (tppId != null && !tppId.isBlank()) {
            uriBuilder.queryParam("tppId", tppId);
        }
        if (cursor != null) {
            uriBuilder.queryParam("cursor", cursor);
        }
        return uriBuilder.build();
    }
}
