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
        private int size;
        private int sizePerRoute;
        private long timeToLiveMinutes;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpClientTimeoutConfig {
        private long connectMillis;
        private long readMillis;
    }
}
