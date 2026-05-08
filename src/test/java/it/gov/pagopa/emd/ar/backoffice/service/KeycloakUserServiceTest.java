package it.gov.pagopa.emd.ar.backoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.domain.model.Organization;
import it.gov.pagopa.emd.ar.backoffice.domain.model.Role;
import it.gov.pagopa.emd.ar.backoffice.domain.model.User;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KeycloakUserService} using Spring WebFlux's {@link ExchangeFunction}.
 *
 * <p>Instead of spinning up a real HTTP server, we inject a custom {@link ExchangeFunction}
 * that returns pre-programmed {@link ClientResponse} instances. This approach:
 * <ul>
 *   <li>Exercises the real WebClient code paths — including all {@code onStatus} handlers.</li>
 *   <li>Has zero external dependencies beyond what Spring Boot Test already provides.</li>
 *   <li>Runs in-process: no ports opened, no OkHttp version conflicts.</li>
 * </ul>
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>User does NOT exist → created (POST /users) then linked (POST /federated-identity)</li>
 *   <li>User EXISTS → updated (PUT /users/{id}) then linked (POST /federated-identity)</li>
 *   <li>User EXISTS and ALREADY LINKED → updated, 409 on link silently skipped (no error)</li>
 *   <li>GET /users fails → error propagates, no further HTTP calls</li>
 * </ol>
 */
class KeycloakUserServiceTest {

    private static final String BASE_URL    = "https://keycloak.test";
    private static final String REALM       = "test-realm";
    private static final String IDP_ALIAS   = "selfcare";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String KC_ID       = "kc-internal-id-001";
    private static final String USER_UID    = "user-uid-abc";

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a test User with all required fields. */
    private User buildUser() {
        Role role = new Role();
        role.setRole("admin");
        Organization org = new Organization();
        org.setId("ORG-001");
        org.setName("Test Org");
        org.setRoles(List.of(role));
        User user = new User();
        user.setUid(USER_UID);
        user.setName("Mario");
        user.setFamilyName("Rossi");
        user.setEmail("mario@rossi.it");
        user.setOrganization(org);
        return user;
    }

    /** Creates a JSON 200 response. */
    private ClientResponse ok(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    /** Creates a 201 response with a Location header pointing to the new Keycloak resource. */
    private ClientResponse created(String resourceId) {
        return ClientResponse.create(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION,
                        BASE_URL + "/admin/realms/" + REALM + "/users/" + resourceId)
                .body("")
                .build();
    }

    /** Creates a no-content 204 response. */
    private ClientResponse noContent() {
        return ClientResponse.create(HttpStatus.NO_CONTENT).body("").build();
    }

    /** Creates a 409 Conflict response with Keycloak's "already linked" body. */
    private ClientResponse alreadyLinked() {
        return ClientResponse.create(HttpStatus.CONFLICT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"errorMessage\":\"User is already linked with provider\"}")
                .build();
    }

    /** Creates a 500 error response. */
    private ClientResponse serverError() {
        return ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"server_error\",\"error_description\":\"Internal error\"}")
                .build();
    }

    /**
     * Builds a {@link KeycloakUserService} whose HTTP calls are intercepted by the provided
     * {@link ExchangeFunction}.
     */
    private KeycloakUserService serviceWith(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        return new KeycloakUserService(webClient, new ObjectMapper(), BASE_URL, REALM, IDP_ALIAS);
    }

    /**
     * Builds an ExchangeFunction that returns responses from a FIFO queue and records each
     * request's method+path in the provided list (for assertions).
     */
    private ExchangeFunction queuedExchange(Queue<ClientResponse> responses,
                                             List<String> recordedRequests) {
        return request -> {
            recordedRequests.add(request.method().name() + " " + request.url().getPath());
            ClientResponse next = responses.poll();
            if (next == null) {
                throw new IllegalStateException("No more queued responses — unexpected request: "
                        + request.method() + " " + request.url());
            }
            return Mono.just(next);
        };
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * User NOT found in Keycloak:
     *   1. GET /users → 200 []
     *   2. POST /users → 201 (Location: .../KC_ID)
     *   3. POST /federated-identity → 204
     * Expected: completes without error; all 3 calls made in order.
     */
    @Test
    void upsertKeycloakUser_UserNotFound_CreatesAndLinks() {
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok("[]"),           // GET /users
                created(KC_ID),     // POST /users
                noContent()         // POST /federated-identity
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakUserService svc = serviceWith(queuedExchange(responses, calls));

        StepVerifier.create(svc.upsertKeycloakUser(ADMIN_TOKEN, buildUser()))
                .verifyComplete();

        assertThat(calls).hasSize(3);
        assertThat(calls.get(0)).startsWith("GET").contains("/users");
        assertThat(calls.get(1)).startsWith("POST").endsWith("/users");
        assertThat(calls.get(2)).startsWith("POST").contains("federated-identity");
    }

    /**
     * User EXISTS in Keycloak:
     *   1. GET /users → 200 [{id: KC_ID}]
     *   2. PUT /users/{KC_ID} → 204
     *   3. POST /federated-identity → 204
     * Expected: completes without error; PUT (not POST) used for user.
     */
    @Test
    void upsertKeycloakUser_UserFound_UpdatesAndLinks() {
        String existingUser = "[{\"id\":\"" + KC_ID + "\",\"username\":\"" + USER_UID + "\"}]";
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(existingUser),   // GET /users
                noContent(),        // PUT /users/{KC_ID}
                noContent()         // POST /federated-identity
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakUserService svc = serviceWith(queuedExchange(responses, calls));

        StepVerifier.create(svc.upsertKeycloakUser(ADMIN_TOKEN, buildUser()))
                .verifyComplete();

        assertThat(calls).hasSize(3);
        assertThat(calls.get(0)).startsWith("GET");
        assertThat(calls.get(1)).startsWith("PUT").contains("/users/" + KC_ID);
        assertThat(calls.get(2)).startsWith("POST").contains("federated-identity");
    }

    /**
     * User EXISTS and is ALREADY LINKED:
     *   1. GET /users → 200 [{id: KC_ID}]
     *   2. PUT /users/{KC_ID} → 204
     *   3. POST /federated-identity → 409 Conflict ("User is already linked with provider")
     * Expected: completes WITHOUT error — 409 must be silently skipped.
     */
    @Test
    void upsertKeycloakUser_AlreadyLinked_409IsSkippedNoError() {
        String existingUser = "[{\"id\":\"" + KC_ID + "\",\"username\":\"" + USER_UID + "\"}]";
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(existingUser),   // GET /users
                noContent(),        // PUT /users/{KC_ID}
                alreadyLinked()     // POST /federated-identity → 409
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakUserService svc = serviceWith(queuedExchange(responses, calls));

        StepVerifier.create(svc.upsertKeycloakUser(ADMIN_TOKEN, buildUser()))
                .verifyComplete(); // ← must NOT emit any error

        assertThat(calls).hasSize(3);
        assertThat(calls.get(2)).startsWith("POST").contains("federated-identity");
    }

    /**
     * GET /users fails (500):
     *   error propagates as ExternalServiceException, no further calls made.
     */
    @Test
    void upsertKeycloakUser_GetUsersFails_ErrorPropagated() {
        Queue<ClientResponse> responses = new LinkedList<>(List.of(serverError()));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakUserService svc = serviceWith(queuedExchange(responses, calls));

        StepVerifier.create(svc.upsertKeycloakUser(ADMIN_TOKEN, buildUser()))
                .expectErrorMatches(ex -> ex.getMessage() != null
                        && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst()).startsWith("GET");
    }
}

