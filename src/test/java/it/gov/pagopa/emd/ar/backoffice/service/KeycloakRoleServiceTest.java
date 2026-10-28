package it.gov.pagopa.emd.ar.backoffice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakRoleServiceImpl;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakTokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KeycloakRoleServiceImpl} using Spring WebFlux's {@link ExchangeFunction}.
 */
class KeycloakRoleServiceTest {

    private static final String BASE_URL = "https://keycloak.test";
    private static final String REALM = "test-realm";
    private static final String TARGET_CLIENT_ID = "ar-backoffice-portal-client";
    private static final String CLIENT_INTERNAL_ID = "uuid-client-123";
    private static final String USER_ID = "uuid-user-456";
    private static final String ADMIN_TOKEN = "admin-token";

    private KeycloakTokenService tokenServiceMock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tokenServiceMock = mock(KeycloakTokenService.class);
        when(tokenServiceMock.getManagerToken()).thenReturn(Mono.just(ADMIN_TOKEN));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ClientResponse ok(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    private ClientResponse noContent() {
        return ClientResponse.create(HttpStatus.NO_CONTENT).body("").build();
    }

    private ClientResponse serverError() {
        return ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"server_error\",\"error_description\":\"Internal error\"}")
                .build();
    }

    private KeycloakRoleServiceImpl serviceWith(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        return new KeycloakRoleServiceImpl(
                webClient, 
                objectMapper, 
                tokenServiceMock, 
                BASE_URL, 
                REALM, 
                TARGET_CLIENT_ID);
    }

    private ExchangeFunction queuedExchange(Queue<ClientResponse> responses, List<String> recordedRequests) {
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

    // ── Tests per getAvailableClientRoles ────────────────────────────────────

    @Test
    void getAvailableClientRoles_Success() {
        String clientResolutionJson = "[{\"id\":\"" + CLIENT_INTERNAL_ID + "\"}]";
        String rolesJson = "[{\"id\":\"role-id-1\",\"name\":\"operator-read\"}]";

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientResolutionJson), // GET /clients (resolve internal ID)
                ok(rolesJson)             // GET /roles
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakRoleServiceImpl svc = serviceWith(queuedExchange(responses, calls));

        StepVerifier.create(svc.getAvailableClientRoles())
                .assertNext(roles -> {
                    assertThat(roles).hasSize(1);
                    assertThat(roles.get(0).getId()).isEqualTo("role-id-1");
                    assertThat(roles.get(0).getName()).isEqualTo("operator-read");
                })
                .verifyComplete();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
        assertThat(calls.get(1)).startsWith("GET").contains("/roles");
    }

    @Test
    void getAvailableClientRoles_ClientNotFound_ThrowsError() {
        String emptyClientList = "[]";

        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(emptyClientList) // GET /clients returns empty
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakRoleServiceImpl svc = serviceWith(queuedExchange(responses, calls));

        StepVerifier.create(svc.getAvailableClientRoles())
                .expectErrorMatches(ex -> ex.getMessage() != null && ex.getMessage().contains("Target client not found"))
                .verify();

        assertThat(calls).hasSize(1); // Non fa la seconda chiamata ai roles
    }

    // ── Tests per replaceUserRoles ───────────────────────────────────────────

    @Test
    void replaceUserRoles_UserHasRoles_RolesReplacedSuccessfully() {
        String clientResolutionJson = "[{\"id\":\"" + CLIENT_INTERNAL_ID + "\"}]";
        String currentRolesJson = "[{\"id\":\"role-old\",\"name\":\"operator-read\"}]";
        
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientResolutionJson), // 1. Resolve client UUID
                ok(currentRolesJson),     // 2. GET current user roles
                noContent(),              // 3. DELETE old roles
                noContent()               // 4. POST new roles
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakRoleServiceImpl svc = serviceWith(queuedExchange(responses, calls));

        List<KeycloakRoleDTOV1> newRoles = List.of(new KeycloakRoleDTOV1("role-new", "operator-write", ""));

        StepVerifier.create(svc.replaceUserRoles(USER_ID, newRoles))
                .verifyComplete();

        assertThat(calls).hasSize(4);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
        assertThat(calls.get(1)).startsWith("GET").contains("/role-mappings");
        assertThat(calls.get(2)).startsWith("DELETE").contains("/role-mappings");
        assertThat(calls.get(3)).startsWith("POST").contains("/role-mappings");
    }

    @Test
    void replaceUserRoles_UserHasNoRoles_OnlyPostCalled() {
        String clientResolutionJson = "[{\"id\":\"" + CLIENT_INTERNAL_ID + "\"}]";
        String currentRolesEmptyJson = "[]"; // User has no roles
        
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientResolutionJson), // 1. Resolve client UUID
                ok(currentRolesEmptyJson),// 2. GET current user roles
                noContent()               // 3. POST new roles (DELETE is skipped)
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakRoleServiceImpl svc = serviceWith(queuedExchange(responses, calls));

        List<KeycloakRoleDTOV1> newRoles = List.of(new KeycloakRoleDTOV1("role-new", "operator-write", ""));

        StepVerifier.create(svc.replaceUserRoles(USER_ID, newRoles))
                .verifyComplete();

        assertThat(calls).hasSize(3);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
        assertThat(calls.get(1)).startsWith("GET").contains("/role-mappings");
        assertThat(calls.get(2)).startsWith("POST").contains("/role-mappings");
    }

    @Test
    void replaceUserRoles_NewRolesEmpty_OnlyDeleteCalled() {
        String clientResolutionJson = "[{\"id\":\"" + CLIENT_INTERNAL_ID + "\"}]";
        String currentRolesJson = "[{\"id\":\"role-old\",\"name\":\"operator-read\"}]";
        
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientResolutionJson), // 1. Resolve client UUID
                ok(currentRolesJson),     // 2. GET current user roles
                noContent()               // 3. DELETE old roles (POST is skipped)
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakRoleServiceImpl svc = serviceWith(queuedExchange(responses, calls));

        List<KeycloakRoleDTOV1> emptyNewRoles = List.of(); // Empty list

        StepVerifier.create(svc.replaceUserRoles(USER_ID, emptyNewRoles))
                .verifyComplete();

        assertThat(calls).hasSize(3);
        assertThat(calls.get(0)).startsWith("GET").contains("/clients");
        assertThat(calls.get(1)).startsWith("GET").contains("/role-mappings");
        assertThat(calls.get(2)).startsWith("DELETE").contains("/role-mappings");
    }

    @Test
    void replaceUserRoles_KeycloakReturns500OnDelete_ErrorPropagatedAndPostSkipped() {
        String clientResolutionJson = "[{\"id\":\"" + CLIENT_INTERNAL_ID + "\"}]";
        String currentRolesJson = "[{\"id\":\"role-old\",\"name\":\"operator-read\"}]";
        
        Queue<ClientResponse> responses = new LinkedList<>(List.of(
                ok(clientResolutionJson), // 1. Resolve client UUID
                ok(currentRolesJson),     // 2. GET current user roles
                serverError()             // 3. DELETE fails with 500
        ));
        List<String> calls = new CopyOnWriteArrayList<>();
        KeycloakRoleServiceImpl svc = serviceWith(queuedExchange(responses, calls));

        List<KeycloakRoleDTOV1> newRoles = List.of(new KeycloakRoleDTOV1("role-new", "operator-write", ""));

        StepVerifier.create(svc.replaceUserRoles(USER_ID, newRoles))
                .expectErrorMatches(ex -> ex.getMessage() != null && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        assertThat(calls).hasSize(3);
        assertThat(calls.get(0)).startsWith("GET");
        assertThat(calls.get(1)).startsWith("GET");
        assertThat(calls.get(2)).startsWith("DELETE");
        // POST was skipped due to DELETE failure
    }
}