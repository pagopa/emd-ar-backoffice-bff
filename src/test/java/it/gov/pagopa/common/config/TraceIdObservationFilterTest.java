package it.gov.pagopa.common.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceIdObservationFilterTest {

    @Mock
    private Tracer tracerMock;

    @Mock
    private Span spanMock;

    @Mock
    private TraceContext traceContextMock;

    @Mock
    private WebFilterChain filterChainMock;

    private TraceIdObservationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdObservationFilter(tracerMock);
    }

    @AfterEach
    void verifyNoMoreInteractions() {
        // Verifichiamo che la catena dei filtri sia stata chiamata esattamente una volta
        Mockito.verify(filterChainMock).filter(any(MockServerWebExchange.class));
        Mockito.verifyNoMoreInteractions(filterChainMock);
    }

    @Test
    void givenTraceIdWhenFilterThenAddHeader() {
        // Given
        String traceId = "mock-trace-id";
        
        // Mock della catena Micrometer: tracer -> currentSpan -> context -> traceId
        when(tracerMock.currentSpan()).thenReturn(spanMock);
        when(spanMock.context()).thenReturn(traceContextMock);
        when(traceContextMock.traceId()).thenReturn(traceId);
        
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
        when(filterChainMock.filter(any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = filter.filter(exchange, filterChainMock);

        // Then
        StepVerifier.create(result).verifyComplete();
        
        // Verifichiamo che l'header X-Trace-Id sia presente nella risposta
        assertEquals(traceId, exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void givenNoSpanWhenFilterThenDoNothing() {
        // Given: il tracer non ha alcuno span attivo (ritorna null)
        when(tracerMock.currentSpan()).thenReturn(null);
        
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
        when(filterChainMock.filter(any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = filter.filter(exchange, filterChainMock);

        // Then
        StepVerifier.create(result).verifyComplete();
        
        // Verifichiamo che l'header NON sia stato aggiunto
        assertNull(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
    }

}