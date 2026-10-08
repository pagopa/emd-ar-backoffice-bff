package it.gov.pagopa.common.utils;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.Base64;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;


@Component
public class Utilities {

    private final Tracer tracer;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Utilities(Tracer tracer) {
        this.tracer = tracer;
    }

    public String getTraceId() {
        Span span = tracer.currentSpan();
        return span != null
            ? span.context().traceId()
            : "no-trace-id";
    }

    /**
     * Helper to extract the email from the Authorization header containing a Bearer token.
     */
    public static String getEmailFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "Email not available (no Authorization header or not Bearer token)";
        }
        try {
            // Remove "Bearer " prefix
            String token = authHeader.substring(7);
            String[] chunks = token.split("\\.");
            if (chunks.length < 2)
                return "invalid-token";

            // Decode payload (second part of the JWT)
            String payload = new String(Base64.getUrlDecoder().decode(chunks[1]));
            
            // Read email from the payload JSON
            JsonNode node = objectMapper.readTree(payload);
            return node.has("email") ? node.get("email").asText() : "unknown-user";
        } catch (Exception e) {
            return "error-parsing-token";
        }
    }
}