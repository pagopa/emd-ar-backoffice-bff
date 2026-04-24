package it.gov.pagopa.common.performancelogger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import it.gov.pagopa.common.utils.MemoryAppender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ApiRequestPerformanceLoggerTest {

    @Mock
    private WebFilterChain filterChainMock;

    private MemoryAppender memoryAppender;
    private ApiRequestPerformanceLogger filter;

    @BeforeEach
    void init() {
            filter = new ApiRequestPerformanceLogger();
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            // Crea l'appender manualmente per avere il massimo controllo
            memoryAppender = new MemoryAppender();
            memoryAppender.setContext(context);
            memoryAppender.setName("TEST_APPENDER");
            memoryAppender.start();

            // Lo collega a tutti i logger potenziali
            String[] loggerNames = {
                    "it.gov.pagopa",
                    "API_REQUEST",
                    ApiRequestPerformanceLogger.class.getName()
            };

            for (String name : loggerNames) {
            Logger l = context.getLogger(name);
            l.setLevel(Level.INFO);
            l.addAppender(memoryAppender);
            }
    }

    @Test
    void givenCoveredPathWhenFilterThenPerformanceLog() {
            String path = "/api/test";
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build()
            );
            Mockito.when(filterChainMock.filter(any())).thenReturn(Mono.empty());

            // Usa StepVerifier per consumare il flusso in modo reattivo e sicuro
            StepVerifier.create(filter.filter(exchange, filterChainMock))
                    .expectComplete()
                    .verify();

        	// Per debug, se fallisce stampa cosa ha catturato realmente
            List<?> events = memoryAppender.getLoggedEvents();
                
            boolean logPresente = events.stream()
                    .anyMatch(event -> event.toString().contains(path));

            Assertions.assertTrue(logPresente, "Log non trovato! Eventi catturati: " + events);
    }

    @Test
    void givenNotCoveredPathWhenFilterThenDontPerformanceLog() {
            String path = "/actuator/health";
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build()
            );
            Mockito.when(filterChainMock.filter(any())).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChainMock))
                    .expectComplete()
                    .verify();

            boolean logDurataPresente = memoryAppender.getLoggedEvents().stream()
                    .anyMatch(event -> event.toString().contains(path) && event.toString().contains("Duration"));

            Assertions.assertFalse(logDurataPresente, "Non dovrebbe loggare la durata per i path esclusi");
    }

    @Test
    void givenErrorResponseWhenFilterThenPerformanceLog() {
            String path = "/api/error";
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).build()
            );
                
            Mockito.when(filterChainMock.filter(any()))
                    .thenReturn(Mono.error(new RuntimeException("Simulated Error")));

            // Il filtro deve gestire l'errore e loggare comunque
            StepVerifier.create(filter.filter(exchange, filterChainMock))
                    .expectError()
                    .verify();

            boolean logPresente = memoryAppender.getLoggedEvents().stream()
                    .anyMatch(event -> event.toString().contains(path));
                
            Assertions.assertTrue(logPresente, "Il log deve essere generato anche se il flusso fallisce");
    }

    @Test
    void givenExceptionDuringProcessingThenEnsureChainIsCalled() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/test").build()
            );
            Mockito.when(filterChainMock.filter(any())).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, filterChainMock))
                    .expectComplete()
                    .verify();

            Mockito.verify(filterChainMock, Mockito.times(1)).filter(any());
    }
}