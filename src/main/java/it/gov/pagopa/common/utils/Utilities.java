package it.gov.pagopa.common.utils;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;


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

    /**
     * Helper to extract the email from the Authorization header containing a Bearer token.
     */
    public static String getEmailFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "Email not available (no Authorization header or not Bearer token)";
        }
        try {
            String token = authHeader.substring(7);
            DecodedJWT jwt = JWT.decode(token);
            String email = jwt.getClaim("email").asString();
            return email != null ? email : "unknown-user";
        } catch (Exception e) {
            return "error-parsing-token";
        }
    }

}
