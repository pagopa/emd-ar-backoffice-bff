package it.gov.pagopa.emd.ar.backoffice.service;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.interfaces.RSAPublicKey;

@Component
@Slf4j
public class SelfCareTokenValidator {

    private final String expectedIssuer;
    private final String expectedAudience;
    private final JwkProvider jwkProvider;

    public SelfCareTokenValidator(
            JwkProvider jwkProvider,
            @Value("${auth.sc.expected-issuer}") String expectedIssuer,
            @Value("${auth.sc.expected-audience}") String expectedAudience) {
        this.jwkProvider = jwkProvider;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        log.info("[AR-BFF][SELFCARE_TOKEN_VALIDATOR] Initialized: issuer={} audience={}", expectedIssuer, expectedAudience);
    }

    /**
     * Validates the provided SelfCare JWT token.
     * <p>
     * Since {@code jwkProvider.get()} performs a blocking network call (I/O) to fetch the
     * public key, this operation is wrapped in a {@link Mono#fromCallable(java.util.concurrent.Callable)}
     * and offloaded to {@link Schedulers#boundedElastic()}. This ensures that the
     * WebFlux event loop remains non-blocking.
     * The public key is already cached by {@link JwkProvider} (24h), so no additional
     * caching is needed here.
     *
     * @param token The raw SelfCare JWT string to be validated.
     * @return A {@link Mono} emitting the {@link DecodedJWT} if successful,
     *         or an error (e.g., JWTVerificationException) if validation fails.
     */
    public Mono<DecodedJWT> validate(String token) {
        return Mono.fromCallable(() -> {
            DecodedJWT jwt = JWT.decode(token);
            String kid = jwt.getKeyId();

            Jwk jwk = jwkProvider.get(kid);
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(expectedIssuer)
                    .withAudience(expectedAudience)
                    .build();

            return verifier.verify(token);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> log.error("[AR-BFF][VALIDATE_SC_TOKEN] Token validation failed: {}", e.getMessage()));
    }
}
