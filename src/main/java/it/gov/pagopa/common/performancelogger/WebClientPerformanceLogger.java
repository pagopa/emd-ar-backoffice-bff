package it.gov.pagopa.common.performancelogger;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import it.gov.pagopa.common.utils.SecurityUtils;

public class WebClientPerformanceLogger implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long startTime = System.currentTimeMillis();
        
        return next.exchange(request)
            .doOnEach(signal -> {
                if (signal.isOnNext() || signal.isOnError()) {
                    String payload = signal.isOnNext() 
                        ? "HttpStatus: " + signal.get().statusCode().value() 
                        : "Exception: " + signal.getThrowable().getMessage();
                    
                    PerformanceLogger.log(
                            "REST_INVOKE",
                            getRequestDetails(request),
                            startTime,
                            payload,
                            null);
                }
            });
    }

    private String getRequestDetails(ClientRequest request) {
        return "%s %s".formatted(request.method(), SecurityUtils.removePiiFromURI(request.url()));
    }
}
