package it.gov.pagopa.common.utils;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;


@Component
public class Utilities {

    private static final String CITIZEN_PATH_PREFIX = "/emd/backoffice/api/v1/citizen/";
    // Static citizen routes: the first segment is not a fiscal code.
    private static final Set<String> CITIZEN_STATIC_SEGMENTS = Set.of("search", "consent");

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

    /**
     * Returns the hex-encoded SHA-256 of the given value,
     * so a fiscal code can be correlated in logs without being exposed.
     */
    public static String createSHA256(String fiscalCode) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = md.digest(fiscalCode.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every Java platform implementation
            throw new IllegalStateException("Something went wrong creating SHA256", e);
        }
    }

    /**
     * Replaces the fiscal code in a citizen request path with its SHA-256 (see {@link #createSHA256}),
     * so that the path can be logged without exposing personal data.
     *
     * <p>The segment right after {@code /emd/backoffice/api/v1/citizen/} is treated as the fiscal code
     * and hashed as received (no case normalization), while any following segments are kept.
     * For example {@code /emd/backoffice/api/v1/citizen/{fiscalCode}/consents/{tppId}} becomes
     * {@code /emd/backoffice/api/v1/citizen/{sha256}/consents/{tppId}}.</p>
     *
     * <p>The path is returned unchanged when it does not contain the citizen prefix, when nothing
     * follows the prefix, or when that first segment is a static route name ({@code search} or
     * {@code consent}, i.e. {@code citizen/search} and {@code citizen/consent/search}).</p>
     *
     * @param path the request path to log, may be {@code null}
     * @return the path with the fiscal code hashed, or {@code null} if {@code path} is {@code null}
     */
    public static String hashFiscalCodeInPath(String path) {
        if (path == null) {
            return null;
        }
        int prefixIndex = path.indexOf(CITIZEN_PATH_PREFIX);
        if (prefixIndex < 0) {
            return path;
        }
        int segmentStart = prefixIndex + CITIZEN_PATH_PREFIX.length();
        if (segmentStart >= path.length()) {
            return path;
        }
        int segmentEnd = path.indexOf('/', segmentStart);
        if (segmentEnd < 0) {
            segmentEnd = path.length();
        }
        String segment = path.substring(segmentStart, segmentEnd);
        if (CITIZEN_STATIC_SEGMENTS.contains(segment)) {
            return path;
        }
        return path.substring(0, segmentStart) + createSHA256(segment) + path.substring(segmentEnd);
    }

}
