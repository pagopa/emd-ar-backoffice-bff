package it.gov.pagopa.common.performancelogger;

import it.gov.pagopa.common.utils.MemoryAppender;
import org.junit.jupiter.api.Assertions;
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

@ExtendWith(MockitoExtension.class)
class ApiRequestPerformanceLoggerTest {

    public static final String APPENDER_NAME = "API_REQUEST";

    private MockServerWebExchange exchange;
    
    @Mock
    private WebFilterChain filterChainMock;

    private MemoryAppender memoryAppender;
    private ApiRequestPerformanceLogger filter;

    @BeforeEach
    void init() {
        filter = new ApiRequestPerformanceLogger();
        // Mock del comportamento della catena: deve ritornare Mono.empty() per simulare il successo
        Mockito.when(filterChainMock.filter(Mockito.any())).thenReturn(Mono.empty());
    }

    @BeforeEach
    void setupMemoryAppender() {
        // Presumo che PerformanceLoggerTest sia già stato migrato o sia compatibile
        this.memoryAppender = PerformanceLoggerTest.buildPerformanceLoggerMemoryAppender(APPENDER_NAME);
    }

    @Test
    void givenNotCoveredPathWhenFilterThenDontPerformanceLog() {
        // Given: una richiesta su un path escluso (es. /actuator)
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build());

        // When: eseguiamo il filtro. In WebFlux dobbiamo sottoscriverci (usiamo .block() nel test)
        filter.filter(exchange, filterChainMock).block();

        // Then
        Assertions.assertEquals(0, memoryAppender.getLoggedEvents().size());
        Mockito.verify(filterChainMock).filter(exchange);
    }

    @Test
    void givenCoveredPathWhenFilterThenPerformanceLog() {
        // Given: una richiesta su un path tracciato
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").build());

        // When
        filter.filter(exchange, filterChainMock).block();

        // Then
        PerformanceLoggerTest.assertPerformanceLogMessage(APPENDER_NAME, "GET /api/test", "HttpStatus: 200", memoryAppender);
        
        // Verifichiamo che la catena sia stata chiamata
        Mockito.verify(filterChainMock).filter(exchange);
    }

    @Test
    void givenErrorResponseWhenFilterThenPerformanceLog() {
        // Given: una richiesta che simula un errore (es. 404)
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/not-found").build());
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);

        // When
        filter.filter(exchange, filterChainMock).block();

        // Then
        PerformanceLoggerTest.assertPerformanceLogMessage(APPENDER_NAME, "GET /api/not-found", "HttpStatus: 404", memoryAppender);
    }
}