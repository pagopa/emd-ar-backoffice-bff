package it.gov.pagopa.emd.ar.backoffice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import it.gov.pagopa.common.config.rest.HttpClientConfig;
import it.gov.pagopa.common.config.rest.QueryParamsPlusEncoderInterceptor;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Centralized {@link WebClient.Builder} configuration for all HTTP connectors.
 *
 * <p>Addresses "stale connection" issues caused by Azure load balancers silently
 * closing idle TCP connections. The pool is tuned with explicit {@code maxIdleTime}
 * and {@code maxLifeTime} so connections are recycled before the infrastructure
 * layer discards them.
 *
 * <p>Adds mandatory connect / read / write / response timeouts that Netty does not
 * set by default, preventing thread hangs in reactive pipelines.
 *
 * <p><strong>Bean topology:</strong>
 * <ul>
 *   <li>{@link ConnectionProvider} — singleton; one real Netty pool for the whole app.</li>
 *   <li>{@link HttpClient} — singleton; wraps the pool and applies transport-level timeouts.</li>
 *   <li>{@link WebClient.Builder} — <em>prototype</em>; each connector gets its own independent
 *       builder so that {@code .baseUrl()} mutations in one connector never leak into another.</li>
 *   <li>{@code webClient} — singleton; pre-built client for Keycloak calls (includes
 *       {@link QueryParamsPlusEncoderInterceptor} needed for OAuth2 form params).</li>
 * </ul>
 */
@Configuration
public class WebClientConfig {

    /**
     * Binds {@code rest.defaults.*} properties into a typed config object.
     * The bean is used by {@link #connectionProvider} and {@link #httpClient}.
     */
    @Bean
    public HttpClientConfig httpClientConfig() {
        return new HttpClientConfig();
    }

    /**
     * Singleton {@link ConnectionProvider} — the actual Netty connection pool.
     *
     * <p>A single named pool is shared across all connectors. Reactor Netty maintains
     * one independent connection bucket <strong>per remote host</strong> inside this
     * provider, so there is no contention between connectors pointing to different
     * back-ends.
     *
     * <p>Pool parameters are read from {@code rest.defaults.connection-pool.*}
     * (see {@code application.yml}).
     */
    @Bean
    public ConnectionProvider connectionProvider(HttpClientConfig config) {
        HttpClientConfig.HttpClientConnectionPoolConfig pool = config.getConnectionPool();
        return ConnectionProvider
                .builder("emd-ar-backoffice-http-pool")
                .maxConnections(pool.getSize())
                .pendingAcquireMaxCount(pool.getPendingAcquireMaxCount())
                .pendingAcquireTimeout(Duration.ofSeconds(pool.getPendingAcquireTimeoutSeconds()))
                .maxIdleTime(Duration.ofSeconds(pool.getMaxIdleTimeSeconds()))
                .maxLifeTime(Duration.ofSeconds(pool.getMaxLifeTimeSeconds()))
                .evictInBackground(Duration.ofSeconds(pool.getEvictInBackgroundSeconds()))
                // Expose Reactor Netty pool metrics via Micrometer.
                .metrics(true)
                .build();
    }

    /**
     * Singleton {@link HttpClient} shared by all {@link WebClient} instances.
     *
     * <p>Wraps the shared {@link ConnectionProvider} and applies:
     * <ul>
     *   <li>TCP handshake timeout ({@code CONNECT_TIMEOUT_MILLIS})</li>
     *   <li>End-to-end response timeout ({@link HttpClient#responseTimeout})</li>
     *   <li>Per-event read/write inactivity guards (Netty pipeline handlers)</li>
     * </ul>
     *
     * <p>Timeouts are read from {@code rest.defaults.timeout.*} (see {@code application.yml}).
     */
    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider, HttpClientConfig config) {
        HttpClientConfig.HttpClientTimeoutConfig timeout = config.getTimeout();
        return HttpClient.create(connectionProvider)
                // TCP handshake timeout
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.getConnectMillis())
                // End-to-end response timeout (hard cap for the whole request)
                .responseTimeout(Duration.ofMillis(timeout.getResponseMillis()))
                // Read / Write inactivity guards injected into the Netty pipeline
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeout.getReadMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeout.getReadMillis(), TimeUnit.MILLISECONDS)));
    }

    /**
     * Prototype {@link WebClient.Builder} pre-wired with the shared {@link HttpClient}.
     *
     * <p><strong>Why prototype?</strong> {@link WebClient.Builder} is mutable. A prototype
     * scope ensures that each injection point (connector, service) receives its own
     * independent builder instance, so that calling {@code .baseUrl()} or adding filters
     * in one connector does not affect others. The underlying {@link HttpClient} and
     * {@link ConnectionProvider} remain singletons — only one pool for the whole app.
     *
     * <p>Callers must finish configuration with {@code .baseUrl(url).build()}.
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public WebClient.Builder webClientBuilder(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    /**
     * Singleton {@link WebClient} dedicated to Keycloak calls.
     *
     * <p>Built from a prototype builder so it carries the same transport-level tuning.
     * Keycloak's OpenID-Connect token endpoint requires the {@code +} character in
     * form bodies to remain un-encoded; the {@link QueryParamsPlusEncoderInterceptor}
     * handles that transparently.
     *
     * <p>No {@code baseUrl} is set here: Keycloak services compose full URIs from
     * the configured {@code keycloak.auth-server-url}.
     */
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .filter(new QueryParamsPlusEncoderInterceptor())
                .build();
    }
}
