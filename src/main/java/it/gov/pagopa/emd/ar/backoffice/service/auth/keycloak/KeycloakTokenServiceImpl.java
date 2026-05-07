package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles Keycloak token acquisition and caching.
 * <p>
 * The manager token (client_credentials) is cached in memory and proactively refreshed
 * 60 seconds before expiry to keep every real request on the warm path.
 * </p>
 */
@Service
@Slf4j
public class KeycloakTokenServiceImpl extends AbstractKeycloakService implements KeycloakTokenService {

    private final WebClient webClient;
    private final String managerClientId;
    private final String managerClientSecret;
    private final String backofficeClientId;
    private final String backofficeClientSecret;

    /**
     * In-memory cache for the Keycloak manager token (client_credentials grant).
     */
    private record CachedToken(String value, Instant expiresAt) {
        boolean isValid() { return Instant.now().isBefore(expiresAt); }
    }

    private final AtomicReference<CachedToken> managerTokenCache = new AtomicReference<>();

    /**
     * Tracks the currently scheduled proactive refresh task.
     */
    private final AtomicReference<Disposable> scheduledRefresh = new AtomicReference<>();

    public KeycloakTokenServiceImpl(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.manager.client-id}") String managerClientId,
            @Value("${keycloak.manager.client-secret}") String managerClientSecret,
            @Value("${keycloak.ar-backoffice.client-id}") String backofficeClientId,
            @Value("${keycloak.ar-backoffice.client-secret}") String backofficeClientSecret) {
        super(authServerUrl, realm, objectMapper);
        this.webClient = webClient;
        this.managerClientId = managerClientId;
        this.managerClientSecret = managerClientSecret;
        this.backofficeClientId = backofficeClientId;
        this.backofficeClientSecret = backofficeClientSecret;
    }

    /**
     * Warmup: pre-fetches the manager token at startup so the first real request finds a warm cache.
     */
    @PostConstruct
    public void init() {
        log.info("[AR-BFF][KC_TOKEN_SERVICE] Initialized: auth-server-url={} realm={}", authServerUrl, realm);
        fetchAndCacheManagerToken()
                .subscribe(
                        t -> log.info("[AR-BFF][KC_TOKEN_SERVICE] Warmup: manager token ready"),
                        e -> log.warn("[AR-BFF][KC_TOKEN_SERVICE] Warmup failed, will retry on first request: {}", e.getMessage())
                );
    }

    /**
     * Cancels any pending proactive refresh on application shutdown to avoid resource leaks.
     */
    @PreDestroy
    public void cleanup() {
        Disposable existing = scheduledRefresh.getAndSet(null);
        if (existing != null && !existing.isDisposed()) {
            existing.dispose();
            log.info("[AR-BFF][KC_TOKEN_SERVICE] Proactive refresh task cancelled on shutdown");
        }
    }

    /** {@inheritDoc} */
    @Override
    public Mono<String> getManagerToken() {
        CachedToken cached = managerTokenCache.get();
        if (cached != null && cached.isValid()) {
            log.info("[AR-BFF][KC_TOKEN_SERVICE] Using cached manager token");
            return Mono.just(cached.value());
        }
        return fetchAndCacheManagerToken();
    }

    /**
     * Fetches a fresh manager token from Keycloak, caches it, and schedules a proactive refresh.
     */
    private Mono<String> fetchAndCacheManagerToken() {
        log.info("[AR-BFF][KC_TOKEN_SERVICE] Requesting new client_credentials token");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", managerClientId);
        formData.add("client_secret", managerClientSecret);

        return webClient.post()
                .uri(tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("fetchManagerToken", body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .map(responseMap -> {
                    String token = (String) responseMap.get("access_token");
                    Number expiresIn = (Number) responseMap.getOrDefault("expires_in", 300);
                    long ttl = expiresIn.longValue();
                    managerTokenCache.set(new CachedToken(token, Instant.now().plusSeconds(ttl)));
                    long refreshDelay = Math.max(0, ttl - 60);
                    scheduleProactiveTokenRefresh(refreshDelay);
                    log.info("[AR-BFF][KC_TOKEN_SERVICE] Token cached for {}s, proactive refresh in {}s", ttl, refreshDelay);
                    return token;
                });
    }

    /**
     * Schedules a background token refresh after {@code delaySeconds}.
     * Cancels any previously scheduled refresh to prevent duplicate chains.
     * <p>
     * Retries up to 3 times with exponential backoff (10 s → 60 s) on transient
     * Keycloak/transport errors. If all retries are exhausted, the cache is invalidated
     * so the next real request triggers a synchronous re-fetch instead of serving
     * a stale or expired token.
     * </p>
     */
    private void scheduleProactiveTokenRefresh(long delaySeconds) {
        Retry retrySpec = Retry.backoff(3, Duration.ofSeconds(10))
                .maxBackoff(Duration.ofSeconds(60))
                .jitter(WebClientRetrySpecs.JITTER)
                .filter(ex -> ex instanceof ExternalServiceException || ex instanceof WebClientRequestException)
                .doBeforeRetry(sig -> log.warn(
                        "[AR-BFF][KC_TOKEN_SERVICE] Proactive refresh retry #{} after error: {}",
                        sig.totalRetries() + 1, sig.failure().getMessage()));

        Disposable newRefresh = Mono.delay(Duration.ofSeconds(delaySeconds))
                .flatMap(ignored -> fetchAndCacheManagerToken().retryWhen(retrySpec))
                .subscribe(
                        token -> log.info("[AR-BFF][KC_TOKEN_SERVICE] Token proactively refreshed"),
                        e -> {
                            log.error("[AR-BFF][KC_TOKEN_SERVICE] Proactive refresh failed permanently after retries, " +
                                    "invalidating cache to force re-fetch on next request: {}", e.getMessage());
                            managerTokenCache.set(null);
                        }
                );
        Disposable existing = scheduledRefresh.getAndSet(newRefresh);
        if (existing != null && !existing.isDisposed()) {
            existing.dispose();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Mono<String> getJwtBearerToken(String externalToken) {
        log.info("[AR-BFF][KC_TOKEN_SERVICE] Requesting JWT-Bearer token exchange");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", backofficeClientId);
        formData.add("client_secret", backofficeClientSecret);
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        formData.add("assertion", externalToken);

        return webClient.post()
                .uri(tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(formData)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("jwtBearerExchange", body)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .map(responseMap -> (String) responseMap.get("access_token"))
                .doOnSuccess(t -> log.info("[AR-BFF][KC_TOKEN_SERVICE] JWT-Bearer token obtained"))
                .doOnError(e -> log.error("[AR-BFF][KC_TOKEN_SERVICE] JWT-Bearer exchange failed: {}", e.getMessage()));
    }
}

