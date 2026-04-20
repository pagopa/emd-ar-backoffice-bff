package it.gov.pagopa.common.utils;

import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

@Component
public class Utilities {

    private final Tracer tracer;

    public Utilities(Tracer tracer) {
        this.tracer = tracer;
    }

    public String getTraceId() {
        return (tracer.currentSpan() != null) 
            ? tracer.currentSpan().context().traceId() 
            : "no-trace-id";
    }
}