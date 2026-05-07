package it.gov.pagopa.emd.ar.backoffice.service.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidTokenException;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.domain.model.Organization;
import it.gov.pagopa.emd.ar.backoffice.domain.model.User;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakTokenService;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Orchestrates the SelfCare-to-Keycloak token exchange flow:
 * <ol>
 *   <li>Validate the incoming SelfCare JWT via {@link SelfCareTokenValidator}.</li>
 *   <li>Extract and validate required claims.</li>
 *   <li>Obtain a Keycloak manager token and upsert the user in Keycloak.</li>
 *   <li>Exchange the original token for a Keycloak bearer token and return it.</li>
 * </ol>
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final SelfCareTokenValidator selfCareValidator;
    private final KeycloakTokenService tokenService;
    private final KeycloakUserService userService;
    private final ObjectMapper objectMapper;

    public AuthServiceImpl(
            SelfCareTokenValidator selfCareValidator,
            KeycloakTokenService tokenService,
            KeycloakUserService userService,
            ObjectMapper objectMapper) {
        this.selfCareValidator = selfCareValidator;
        this.tokenService = tokenService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ResponseEntity<AuthResponseV1>> exchangeToken(String token) {
        log.info("[AR-BFF][EXCHANGE_TOKEN] Start");

        if (token == null || token.isBlank()) {
            log.warn("[AR-BFF][EXCHANGE_TOKEN] Rejected: token is null or blank");
            return unauthorizedResponse();
        }

        return selfCareValidator.validate(token)
                .doOnSuccess(jwt -> log.info("[AR-BFF][VALIDATE_SC_TOKEN] Token validated: kid={}", jwt.getKeyId()))
                .doOnError(e -> log.error("[AR-BFF][VALIDATE_SC_TOKEN] Validation failed: {}", e.getMessage()))
                .flatMap(this::verifyARTokenFields)
                .flatMap(user -> {
                    log.info("[AR-BFF][VERIFY_CLAIMS] Claims verified: org_id={}", user.getOrganization().getId());
                    return tokenService.getManagerToken()
                            .flatMap(managerToken -> userService.upsertKeycloakUser(managerToken, user))
                            .then(Mono.defer(() -> tokenService.getJwtBearerToken(token)))
                            .doOnSuccess(t -> log.info("[AR-BFF][EXCHANGE_TOKEN] Completed: org_id={}", user.getOrganization().getId()))
                            .map(finalToken -> ResponseEntity.ok(AuthResponseV1.builder()
                                    .userInfo(user)
                                    .token(finalToken)
                                    .build()));
                })
                .onErrorResume(e -> {
                    log.error("[AR-BFF][EXCHANGE_TOKEN] Failed: {}", e.getMessage());
                    return unauthorizedResponse();
                });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Extracts and validates all required claims from the decoded JWT.
     * Throws {@link InvalidTokenException} when a mandatory claim is absent or blank.
     */
    private Mono<User> verifyARTokenFields(DecodedJWT jwt) {
        return Mono.fromCallable(() -> {
            log.info("[AR-BFF][VERIFY_CLAIMS] Start");

            Map<String, Object> organizationMap = jwt.getClaim("organization").asMap();
            if (organizationMap == null) {
                throw new InvalidTokenException("Missing required claim: organization");
            }

            Organization org = objectMapper.convertValue(organizationMap, Organization.class);

            User user = new User();
            user.setName(requireClaim(jwt, "name"));
            user.setFamilyName(requireClaim(jwt, "family_name"));
            user.setEmail(requireClaim(jwt, "email"));
            user.setUid(requireClaim(jwt, "uid"));
            user.setOrganization(org);

            log.info("[AR-BFF][VERIFY_CLAIMS] Claims valid: org_id={}", org.getId());
            return user;
        }).doOnError(e -> log.error("[AR-BFF][VERIFY_CLAIMS] Failed: {}", e.getMessage()));
    }

    /**
     * Extracts a mandatory string claim from the JWT, throwing {@link InvalidTokenException}
     * if the claim is absent or blank.
     */
    private static String requireClaim(DecodedJWT jwt, String claimName) {
        String value = jwt.getClaim(claimName).asString();
        if (value == null || value.isBlank()) {
            throw new InvalidTokenException("Missing required claim: " + claimName);
        }
        return value;
    }

    private static Mono<ResponseEntity<AuthResponseV1>> unauthorizedResponse() {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseV1.builder()
                        .status("ERROR")
                        .message("Authentication failed")
                        .build()));
    }
}
