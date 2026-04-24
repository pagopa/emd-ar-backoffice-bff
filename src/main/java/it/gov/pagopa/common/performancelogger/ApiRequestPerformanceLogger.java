package it.gov.pagopa.common.performancelogger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ApiRequestPerformanceLogger implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        return chain.filter(exchange)
            .doFinally(signalType -> {
                long duration = System.currentTimeMillis() - startTime;
                if (isPerformanceLoggedRequest(exchange)) {
                    log.info("Request: {} - Duration: {}ms", getRequestDetails(exchange), duration);
                }
            });
    }

    private boolean isPerformanceLoggedRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        // Evita di loggare gli endpoint di health check o simili se necessario
        return !path.contains("/actuator");
    }

    static String getRequestDetails(ServerWebExchange exchange) {
        return "%s %s".formatted(
            exchange.getRequest().getMethod(),
            exchange.getRequest().getPath().value()
        );
    }
}