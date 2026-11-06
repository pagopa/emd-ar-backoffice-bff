package it.gov.pagopa.emd.ar.backoffice.config;

import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the reactive Redisson client used for distributed locking.
 * <p>
 * The {@code redisson-spring-boot-starter} auto-configures the synchronous
 * {@link RedissonClient} from {@code spring.data.redis.*}; this configuration
 * derives the reactive facade from it so the lock service can stay non-blocking
 * on the WebFlux event loop. Both share the same underlying connection manager.
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonReactiveClient redissonReactiveClient(RedissonClient redissonClient) {
        return redissonClient.reactive();
    }
}
