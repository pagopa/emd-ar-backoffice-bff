package it.gov.pagopa.emd.ar.backoffice.config;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class SelfCareJwtConfig {

    @Value("${auth.sc.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Exposes the SelfCare {@link JwkProvider} as a singleton Spring bean.
     * Configured with:
     * <ul>
     *   <li>Cache: up to 10 public keys for 24 hours</li>
     *   <li>Rate limit: max 10 remote JWKS fetches per minute</li>
     * </ul>
     *
     * @throws Exception if the SelfCare JWKS URI is malformed
     */
    @Bean
    public JwkProvider jwkProvider() throws Exception {
        JwkProvider provider = new JwkProviderBuilder(URI.create(jwkSetUri).toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build();

        log.info("[AR-BFF][SELFCARE_JWT_CONFIG] JwkProvider initialized: jwk-set-uri={}", jwkSetUri);
        return provider;
    }
}
