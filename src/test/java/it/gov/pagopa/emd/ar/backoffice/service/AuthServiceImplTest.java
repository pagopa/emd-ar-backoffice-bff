package it.gov.pagopa.emd.ar.backoffice.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.AuthResponseV1;
import it.gov.pagopa.emd.ar.backoffice.service.auth.AuthServiceImpl;
import it.gov.pagopa.emd.ar.backoffice.service.auth.SelfCareTokenValidator;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakTokenService;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private SelfCareTokenValidator selfCareValidator;

    @Mock
    private KeycloakTokenService tokenService;

    @Mock
    private KeycloakUserService userService;

    @InjectMocks
    private AuthServiceImpl authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Helpers ──────────────────────────────────────────────────────────────

    private DecodedJWT buildValidJwt() {
        DecodedJWT jwt = mock(DecodedJWT.class);

        Claim orgClaim = mock(Claim.class);
        when(orgClaim.asMap()).thenReturn(Map.of(
                "id", "ORG1",
                "name", "Org Name",
                "roles", List.of(Map.of("role", "admin"))
        ));

        Claim nameClaim = mock(Claim.class);
        when(nameClaim.asString()).thenReturn("Mario");

        Claim familyNameClaim = mock(Claim.class);
        when(familyNameClaim.asString()).thenReturn("Rossi");

        Claim emailClaim = mock(Claim.class);
        when(emailClaim.asString()).thenReturn("mario@rossi.it");

        Claim uidClaim = mock(Claim.class);
        when(uidClaim.asString()).thenReturn("mario_uid");

        when(jwt.getClaim("organization")).thenReturn(orgClaim);
        when(jwt.getClaim("name")).thenReturn(nameClaim);
        when(jwt.getClaim("family_name")).thenReturn(familyNameClaim);
        when(jwt.getClaim("email")).thenReturn(emailClaim);
        when(jwt.getClaim("uid")).thenReturn(uidClaim);
        when(jwt.getKeyId()).thenReturn("kid-test");

        return jwt;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Happy path: valid token, user upsert succeeds, JWT-Bearer exchange returns a Keycloak token.
     */
    @Test
    void exchangeToken_Success() {
        ReflectionTestUtils.setField(authService, "objectMapper", objectMapper);

        DecodedJWT jwt = buildValidJwt();
        when(selfCareValidator.validate("test-token")).thenReturn(Mono.just(jwt));
        when(tokenService.getManagerToken()).thenReturn(Mono.just("manager-token"));
        when(userService.upsertKeycloakUser(anyString(), any())).thenReturn(Mono.empty());
        when(tokenService.getJwtBearerToken("test-token")).thenReturn(Mono.just("kc-token"));

        StepVerifier.create(authService.exchangeToken("test-token"))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals("kc-token", response.getBody().getToken());
                    assertEquals("Mario", response.getBody().getUserInfo().getName());
                })
                .verifyComplete();

        verify(userService, times(1)).upsertKeycloakUser(eq("manager-token"), any());
        verify(tokenService, times(1)).getJwtBearerToken("test-token");
    }

    /**
     * Null token → immediate 401 without hitting any downstream service.
     */
    @Test
    void exchangeToken_NullToken_ReturnsUnauthorized() {
        StepVerifier.create(authService.exchangeToken(null))
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                    assertEquals("ERROR", response.getBody().getStatus());
                })
                .verifyComplete();

        verifyNoInteractions(selfCareValidator, tokenService, userService);
    }

    /**
     * Blank token → immediate 401 without hitting any downstream service.
     */
    @Test
    void exchangeToken_BlankToken_ReturnsUnauthorized() {
        StepVerifier.create(authService.exchangeToken("  "))
                .assertNext(response -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()))
                .verifyComplete();

        verifyNoInteractions(selfCareValidator, tokenService, userService);
    }

    /**
     * Token validation failure (bad signature) → 401.
     * Uses a proper JWTVerificationException — the type explicitly handled as 401.
     */
    @Test
    void exchangeToken_ValidationFailure_ReturnsUnauthorized() {
        when(selfCareValidator.validate(anyString()))
                .thenReturn(Mono.error(new JWTVerificationException("Signature invalid")));

        StepVerifier.create(authService.exchangeToken("bad-token"))
                .assertNext(response -> {
                    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
                    assertEquals("Authentication failed", response.getBody().getMessage());
                })
                .verifyComplete();

        verifyNoInteractions(tokenService, userService);
    }

    /**
     * Missing organization claim → 401.
     */
    @Test
    void exchangeToken_MissingOrganizationClaim_ReturnsUnauthorized() {
        ReflectionTestUtils.setField(authService, "objectMapper", objectMapper);

        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim missingClaim = mock(Claim.class);
        when(missingClaim.asMap()).thenReturn(null);
        when(jwt.getClaim("organization")).thenReturn(missingClaim);
        when(selfCareValidator.validate(anyString())).thenReturn(Mono.just(jwt));

        StepVerifier.create(authService.exchangeToken("test-token"))
                .assertNext(response -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()))
                .verifyComplete();
    }

    /**
     * Keycloak manager token fetch failure → error propagates (NOT 401).
     * Keycloak being unavailable is an infrastructure error (→ 502 via global handler),
     * not a user authentication error. The selective onErrorResume correctly lets it propagate.
     */
    @Test
    void exchangeToken_ManagerTokenFailure_PropagatesError() {
        ReflectionTestUtils.setField(authService, "objectMapper", objectMapper);

        DecodedJWT jwt = buildValidJwt();
        when(selfCareValidator.validate(anyString())).thenReturn(Mono.just(jwt));
        when(tokenService.getManagerToken())
                .thenReturn(Mono.error(new RuntimeException("Keycloak unavailable")));

        StepVerifier.create(authService.exchangeToken("test-token"))
                .expectErrorMatches(e -> e instanceof RuntimeException
                        && e.getMessage().equals("Keycloak unavailable"))
                .verify();

        verifyNoInteractions(userService);
    }
}
