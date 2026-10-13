package it.gov.pagopa.emd.ar.backoffice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class TraceHeaderLogFilter implements WebFilter {
  private static final Logger log = LoggerFactory.getLogger(TraceHeaderLogFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    HttpHeaders h = exchange.getRequest().getHeaders();
    String traceparent = h.getFirst("traceparent");
    if (traceparent != null) {
      log.info("TRACE-DEBUG traceparent={}", traceparent);
    }
    return chain.filter(exchange);
  }
}
