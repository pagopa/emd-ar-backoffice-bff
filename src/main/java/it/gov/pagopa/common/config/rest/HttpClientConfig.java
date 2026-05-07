package it.gov.pagopa.common.config.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "rest.defaults")
public class HttpClientConfig {
    @NestedConfigurationProperty
    private HttpClientConnectionPoolConfig connectionPool;
    @NestedConfigurationProperty
    private HttpClientTimeoutConfig timeout;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpClientConnectionPoolConfig {
        /** Maximum live connections per remote host. */
        private int size;
        private int sizePerRoute;
        /** Kept for backward-compat — prefer maxIdleTimeSeconds. */
        private long timeToLiveMinutes;
        /** Max idle time before a pooled connection is evicted (seconds). */
        private long maxIdleTimeSeconds;
        /** Absolute max lifetime of a connection regardless of activity (seconds). */
        private long maxLifeTimeSeconds;
        /** Max requests queued when the pool for a host is full. */
        private int pendingAcquireMaxCount;
        /** Fail-fast timeout waiting for a free connection (seconds). */
        private long pendingAcquireTimeoutSeconds;
        /** Period of the background eviction task (seconds). */
        private long evictInBackgroundSeconds;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpClientTimeoutConfig {
        /** TCP handshake timeout (ms). */
        private long connectMillis;
        /** Netty pipeline read/write inactivity timeout (ms). */
        private long readMillis;
        /** End-to-end response timeout — hard cap for the whole request (ms). */
        private long responseMillis;
    }
}
