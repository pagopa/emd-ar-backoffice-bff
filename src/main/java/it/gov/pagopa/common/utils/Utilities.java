package it.gov.pagopa.common.utils;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

@Component
public class Utilities {

    private final Tracer tracer;

    public Utilities(Tracer tracer) {
        this.tracer = tracer;
    }

    public String getTraceId() {
        Span span = tracer.currentSpan();
        return span != null
            ? span.context().traceId()
            : "no-trace-id";
    }
}