package it.gov.pagopa.common.config;

import reactor.core.publisher.Mono;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.micrometer.tracing.Tracer;
import it.gov.pagopa.common.utils.Utilities;

/**
 * Take an ID trace from the request and set it inside the response header "X-Trace-Id" to make it available for clients.
 */
@Service
@Order(-101) // Set in order to be executed after ServerHttpObservationFilter (which will handle traceId): configured through properties management.observations.http.server.filter.order
public class TraceIdObservationFilter implements WebFilter {

    private final Tracer tracer;

    public TraceIdObservationFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
            .doFirst(() -> {
                // Recuperiamo il traceId corrente dal Tracer di Micrometer
                if (tracer.currentSpan() != null) {
                    String traceId = tracer.currentSpan().context().traceId();
                    exchange.getResponse().getHeaders().add("X-Trace-Id", traceId);
                }
            });
    }
}
