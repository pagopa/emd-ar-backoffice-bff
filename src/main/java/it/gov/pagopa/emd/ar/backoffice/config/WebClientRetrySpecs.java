package it.gov.pagopa.emd.ar.backoffice.config;

import io.netty.channel.ConnectTimeoutException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;

/**
 * Centralized {@link Retry} specifications for all {@code WebClient} calls.
 *
 * <p>Two policies are provided according to the semantic safety of the HTTP operation:
 * <ul>
 *   <li>{@link #transientNetwork()} — broad policy for <em>idempotent</em> operations
 *       (GET, PUT, DELETE). Covers both transport errors and transient HTTP 5xx that
 *       are common during AKS rolling updates.</li>
 *   <li>{@link #connectFailureOnly()} — strict policy that retries only when the
 *       TCP handshake demonstrably failed, which is the only safe scenario for
 *       <em>non-idempotent</em> operations (POST, PATCH).</li>
 * </ul>
 *
 * <p>All specs use exponential back-off with jitter to avoid thundering-herd
 * problems during node restarts.
 */
public final class WebClientRetrySpecs {

    /** Maximum number of retry attempts before propagating the error. */
    public static final int MAX_RETRY_ATTEMPTS = 2;

    /** Base backoff interval — exponential doubling is applied on each retry. */
    public static final Duration MIN_BACKOFF = Duration.ofMillis(100);

    /**
     * Jitter factor in [0, 1] applied to the computed backoff delay.
     * 0.5 means ±50 % randomisation relative to the computed value.
     */
    public static final double JITTER = 0.5;

    private WebClientRetrySpecs() {}

    /**
     * Permissive policy for <strong>idempotent operations</strong> (GET, PUT, DELETE).
     *
     * <p>Retries on:
     * <ul>
     *   <li>{@link WebClientRequestException} — transport-level errors (connection reset,
     *       stale connection from pool, read/write timeout).</li>
     *   <li>HTTP 502 / 503 / 504 — transient gateway errors typical during
     *       Kubernetes rolling updates or AKS node recycling.</li>
     * </ul>
     *
     * <p>Does <em>not</em> retry 4xx or non-transient 5xx.
     *
     * @return a fresh {@link Retry} spec — must NOT be shared across pipelines
     */
    public static Retry transientNetwork() {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .jitter(JITTER)
                .filter(ex -> {
                    if (ex instanceof WebClientRequestException) {
                        return true;
                    }
                    if (ex instanceof WebClientResponseException responseEx) {
                        int status = responseEx.getStatusCode().value();
                        return status == 502 || status == 503 || status == 504;
                    }
                    return false;
                });
    }

    /**
     * Conservative policy: retries only when the request demonstrably did not
     * reach the server (TCP handshake failure).
     *
     * <p><strong>Safe for POST / PATCH</strong>: since the request never hit the
     * server, there is no risk of duplicate side-effects. Does <em>not</em> recover
     * from stale-connection drops mid-stream (use {@link #transientNetwork()} for
     * idempotent operations).
     *
     * @return a fresh {@link Retry} spec — must NOT be shared across pipelines
     */
    public static Retry connectFailureOnly() {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, MIN_BACKOFF)
                .jitter(JITTER)
                .filter(ex -> ex instanceof WebClientRequestException
                        && (ex.getCause() instanceof ConnectException
                            || ex.getCause() instanceof ConnectTimeoutException));
    }
}

