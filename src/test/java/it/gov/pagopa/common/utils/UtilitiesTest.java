package it.gov.pagopa.common.utils;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilitiesTest {

    @Mock
    private Tracer tracerMock;

    @Mock
    private Span spanMock;

    @Mock
    private TraceContext traceContextMock;

    private Utilities utilities;

    @BeforeEach
    void setUp() {
        utilities = new Utilities(tracerMock);
    }

    @Test
    void testGetTraceId() {
        // Given
        String expectedResult = "TRACEID";
        
        // Simuliamo la catena: tracer.currentSpan().context().traceId()
        Mockito.when(tracerMock.currentSpan()).thenReturn(spanMock);
        Mockito.when(spanMock.context()).thenReturn(traceContextMock);
        Mockito.when(traceContextMock.traceId()).thenReturn(expectedResult);

        // When
        String result = utilities.getTraceId();

        // Then
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    void testGetTraceIdWhenNoSpan() {
        // Given: non c'è alcuno span attivo
        Mockito.when(tracerMock.currentSpan()).thenReturn(null);

        // When
        String result = utilities.getTraceId();

        // Then
        Assertions.assertEquals("no-trace-id", result);
    }

    // Questi metodi ora servono solo per i test degli altri componenti che usano MDC (se ancora presenti)
    // Ma per UtilitiesTest sono obsoleti. Li teniamo solo se servono a TraceIdObservationFilterTest
    public static void setTraceId(String traceId) {
        // In WebFlux questo metodo è deprecato, ma lo lasciamo per compatibilità con i tuoi vecchi test
        org.slf4j.MDC.put("traceId", traceId);
    }

    public static void clearTraceIdContext(){
        org.slf4j.MDC.clear();
    }
}