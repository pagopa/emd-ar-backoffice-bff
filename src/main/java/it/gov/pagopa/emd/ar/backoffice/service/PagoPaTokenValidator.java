package it.gov.pagopa.emd.ar.backoffice.service;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PagoPaTokenValidator {

    @Value("${auth.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${auth.expected-issuer}")
    private String expectedIssuer;

    @Value("${auth.expected-audience}")
    private String expectedAudience;

    private JwkProvider jwkProvider;

    /**
     * Initialize the JWK provider after the bean is constructed.
     * <p>
     * This method sets up the {@link JwkProvider} to retrieve public keys from the 
     * remote JWKS endpoint. It is configured with a caching mechanism to store up to 
     * 10 keys for 24 hours, reducing network overhead. Additionally, it implements 
     * a rate limit (10 requests per minute) to protect the application and the 
     * Identity Provider from excessive key retrieval attempts.
     * </p>
     *
     * @throws Exception if the JWK Set URI is malformed or cannot be converted to a valid URL.
     */
    @PostConstruct
    public void init() throws Exception {
        this.jwkProvider = new JwkProviderBuilder(URI.create(jwkSetUri).toURL()) // Usa URI.create().toURL()
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Validates the provided JWT token.
     * <p>
     * Since {@code jwkProvider.get()} performs a blocking network call (I/O) to fetch the 
     * public key, this operation is wrapped in a {@link Mono#fromCallable(java.util.concurrent.Callable)} 
     * and offloaded to {@link Schedulers#boundedElastic()}. This ensures that the 
     * WebFlux event loop remains non-blocking.
     * 
     * @param token The raw JWT string to be validated.
     * @return A {@link Mono} emitting the {@link DecodedJWT} if successful, 
     *         or an error (e.g., JWTVerificationException) if validation fails.
     */
    public Mono<DecodedJWT> validate(String token) {
        return Mono.fromCallable(() -> {
            // Decode the token without verification to extract the Key ID (KID) from the header
            DecodedJWT jwt = JWT.decode(token);
            String kid = jwt.getKeyId();

            // Retrieve the public key from the JWKS provider using the KID
            Jwk jwk = jwkProvider.get(kid);
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();

            // Configure the RSA256 algorithm using the retrieved public key
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);

            // Build the verifier with constraints for Issuer and Audience
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(expectedIssuer)
                    .withAudience(expectedAudience)
                    .build();

            // Execute full validation (signature, expiration, and defined claims)
            return verifier.verify(token);
        })
        .subscribeOn(Schedulers.boundedElastic()) // Offload execution to a thread pool suitable for blocking I/O
        .doOnError(e -> log.error("Error during token validation: {}", e.getMessage()));
    }
}