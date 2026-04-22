package it.gov.pagopa.emd.ar.backoffice.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import org.springframework.security.oauth2.jwt.*;
import reactor.core.publisher.Mono;

@Component
public class PagoPaTokenValidator {

    @Value("${auth.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${auth.expected-issuer}")
    private String expectedIssuer;

    @Value("${auth.expected-audience}")
    private String expectedAudience;

    private ReactiveJwtDecoder jwtDecoder;

    /**
     * Initializes the ReactiveJwtDecoder after the bean construction.
     * Sets up the JWKS endpoint and configures combined validation rules for
     * signature, issuer, and audience.
     */
    @PostConstruct
    public void init() {

        // Initialize the decoder pointing to the remote JWK Set URI
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Validator for the 'iss' (Issuer) claim
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(expectedIssuer);

        // Custom validator for the 'aud' (Audience) claim
        // Checks if the audience list contains the expected client identifier
        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(expectedAudience));
        
        OAuth2TokenValidator<Jwt> combinedValidator = new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);

        // Apply the validators to the decoder
        decoder.setJwtValidator(combinedValidator);
        
        // Saving the private decoder instance for use in the validate method
        this.jwtDecoder = decoder;
    }

    /**
     * Validates a JWT token string.
     * 
     * @param token The raw JWT string to be validated.
     * @return A {@link Mono} emitting the validated {@link Jwt} if successful, 
     *         or an error (e.g., JwtException) if validation fails.
     */
    public Mono<Jwt> validate(String token) {
        return this.jwtDecoder.decode(token);
    }
}
