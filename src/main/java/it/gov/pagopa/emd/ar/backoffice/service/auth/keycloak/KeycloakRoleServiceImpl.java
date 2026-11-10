package it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto.KeycloakRoleDTOV1;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.service.lock.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Implementation of the Keycloak role management services for the backoffice portal client.
 * <p>
 * It reads the roles available on the client and replaces the roles assigned to a user. Every call
 * to Keycloak goes through its Admin REST via a reactive {@link WebClient}, using a "manager" token
 * obtained from {@link KeycloakTokenService}.
 * <p>
 * Role replacement ({@link #replaceUserRoles}) is the critical operation: it is protected by a
 * distributed lock and applied with an add-before-remove ordering, so the user is never left
 * without permissions on a partial failure.
 */
@Service
@Slf4j
public class KeycloakRoleServiceImpl extends AbstractKeycloakService implements KeycloakRoleService {

    /** Reactive client used for all Keycloak Admin REST calls. */
    private final WebClient webClient;
    /** Provides the administrative ("manager") token required by the Admin operations. */
    private final KeycloakTokenService tokenService;
    /** Distributed lock that serializes role replacement per user/client across replicas. */
    private final DistributedLockService lockService;
    /** Textual clientId of the backoffice portal client to operate on. */
    private final String arBackofficePortalClientClientId;

    /**
     * In-memory cache of the target client's internal Keycloak UUID,
     * to avoid repeated resolution calls during role operations.
     */
    private final AtomicReference<String> cachedInternalClientId = new AtomicReference<>();

    /**
     * Constructor with dependency injection.
     *
     * @param webClient                         reactive client towards Keycloak
     * @param objectMapper                      shared JSON mapper (used by the base class)
     * @param tokenService                      service providing the administrative token
     * @param lockService                       distributed lock service
     * @param authServerUrl                     Keycloak authentication server URL
     * @param realm                             target Keycloak realm
     * @param arBackofficePortalClientClientId  clientId of the backoffice portal client
     */
    public KeycloakRoleServiceImpl(WebClient webClient, ObjectMapper objectMapper, KeycloakTokenService tokenService,
            DistributedLockService lockService,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.portal-client.client-id}") String arBackofficePortalClientClientId) { 
        super(authServerUrl, realm, objectMapper);
        this.webClient = webClient;
        this.tokenService = tokenService;
        this.lockService = lockService;
        this.arBackofficePortalClientClientId = arBackofficePortalClientClientId;
    }

    /**
     * Retrieves all roles defined on the backoffice portal client.
     * Get the administrative token, then resolve the client's internal UUID and finally read its roles
     * 
     * @return a {@link Mono} with the list of roles available on the client
     */
    @Override
    public Mono<List<KeycloakRoleDTOV1>> getAvailableClientRoles() {
        log.info("[AR-BFF][GET_ROLES] Fetching available roles for client: {}", arBackofficePortalClientClientId);
        return tokenService.getManagerToken()
                .flatMap(adminToken -> getTargetInternalClientId(adminToken)
                        .flatMap(internalId -> fetchClientRoles(adminToken, internalId)));
    }

    /**
     * Completely replaces a user's roles on the backoffice portal client.
     * <p>
     * The whole read-modify-write sequence runs under a distributed lock keyed by
     * {@code clientId:userId}, so two concurrent replacements on the same user/client cannot
     * interleave and leave the union of both requested sets. Only operations on the same
     * user/client are serialized: different users do not block each other.
     *
     * @param userId         internal Keycloak UUID of the user
     * @param requestedRoles complete list of roles the user must own at the end
     * @return a {@link Mono} completing when the replacement is done
     */
    @Override
    public Mono<Void> replaceUserRoles(String userId, List<KeycloakRoleDTOV1> requestedRoles) {
        log.info("[AR-BFF][REPLACE_ROLES] Replacing roles for user {} on client {}", userId, arBackofficePortalClientClientId);

        // Build the per user/client lock key and wrap the whole critical sequence.
        String lockKey = arBackofficePortalClientClientId + ":" + userId;
        return lockService.executeWithLock(lockKey, doReplaceUserRoles(userId, requestedRoles));
    }

    /**
     * Performs the actual role replacement. Must be invoked inside the distributed lock.
     * <p>
     * Validates the requested roles against the client before any mutation, applies the change
     * with an add-before-remove ordering, then performs a final verification by re-reading the
     * actual state. The {@link Mono} completes (and the controller returns 204) only if the
     * observed state matches the requested one; otherwise it fails with an explicit uncertain-state
     * error, so the caller is never told of a misleading success.
     * <p>
     * The operations performed, in order, are:
     * <ol>
     *   <li>Obtain the administrative ("manager") token.</li>
     *   <li>Resolve the target client's internal UUID.</li>
     *   <li>Validate that every requested role exists on the client, before any mutation.</li>
     *   <li>Read the user's current roles on the client.</li>
     *   <li>Apply the change with add-before-remove ordering (POST the requested roles, then
     *       DELETE the no-longer-requested ones).</li>
     *   <li>Verify the final state by re-reading the roles and confirming they match the request.</li>
     * </ol>
     *
     * @param userId         internal UUID of the user
     * @param requestedRoles requested roles
     * @return a {@link Mono} completing only if the final state is confirmed
     */
    private Mono<Void> doReplaceUserRoles(String userId, List<KeycloakRoleDTOV1> requestedRoles) {
        return tokenService.getManagerToken()
                .flatMap(adminToken -> getTargetInternalClientId(adminToken)
                        .flatMap(internalId -> validateRequestedRoles(adminToken, internalId, requestedRoles)
                                .then(getUserClientRoles(adminToken, userId, internalId))
                                .flatMap(currentRoles -> applyRoleChanges(
                                        adminToken, userId, internalId, currentRoles, requestedRoles))
                                .then(verifyFinalState(adminToken, userId, internalId, requestedRoles))));
    }

    /**
     * Final outcome verification.
     * <p>
     * Re-reads the roles actually assigned to the user and compares them with the requested ones.
     * This turns an uncertain remote outcome (for example a timeout after which it is unknown
     * whether Keycloak applied the change) into certainty about the real state: so 204 is emitted
     * only when the state is truly the requested one. If the observed state diverges, it fails with
     * a dedicated error (HTTP 502) instead of a misleading 204; the operation stays idempotent, so
     * the caller can safely retry.
     *
     * @param adminToken     administrative token
     * @param userId         internal UUID of the user
     * @param internalId     internal UUID of the client
     * @param requestedRoles requested roles to compare the final state against
     * @return an empty {@link Mono} if the state is confirmed, otherwise an uncertain-state error
     */
    private Mono<Void> verifyFinalState(String adminToken, String userId, String internalId,
            List<KeycloakRoleDTOV1> requestedRoles) {

        // Set of requested ids: the "expected" state at the end of the operation.
        Set<String> requestedIds = requestedRoles.stream()
                .map(KeycloakRoleDTOV1::getId)
                .collect(Collectors.toSet());

        return getUserClientRoles(adminToken, userId, internalId)
                .flatMap(afterRoles -> {
                    // Set of ids actually present after the mutations.
                    Set<String> afterIds = afterRoles.stream()
                            .map(KeycloakRoleDTOV1::getId)
                            .collect(Collectors.toSet());

                    // Confirmed state: matches exactly the requested one -> success (204).
                    if (afterIds.equals(requestedIds)) {
                        log.info("[AR-BFF][REPLACE_ROLES] Final state confirmed for user {}", userId);
                        return Mono.empty();
                    }

                    // Misaligned/uncertain state: do not return 204, fail with an explicit error.
                    log.warn("[AR-BFF][REPLACE_ROLES] Final state not aligned for user {}: expected {}, found {}",
                            userId, requestedIds, afterIds);
                    return Mono.error(new ExternalServiceException("KEYCLOAK", "replaceUserRoles",
                            "Role state could not be confirmed after replacement (expected " + requestedIds
                                    + ", found " + afterIds + "); the operation is idempotent and can be retried", 502));
                });
    }

    /**
     * Ensures every requested role actually exists on the target client before any mutation.
     * Uses {@link #fetchClientRoles} as the source of truth to reject unknown roles or roles
     * belonging to other clients up front, so a POST never fails halfway leaving the user in an
     * inconsistent state.
     *
     * @param adminToken     administrative token
     * @param internalId     internal UUID of the client
     * @param requestedRoles requested roles to validate
     * @return an empty {@link Mono} if all roles are valid, otherwise an error
     */
    private Mono<Void> validateRequestedRoles(String adminToken, String internalId, List<KeycloakRoleDTOV1> requestedRoles) {
        // Empty list: nothing to validate (valid case, means "remove all roles").
        if (requestedRoles.isEmpty()) {
            return Mono.empty();
        }
        return fetchClientRoles(adminToken, internalId)
                .flatMap(clientRoles -> {
                    // Set of ids of the roles actually defined on the client.
                    Set<String> validIds = clientRoles.stream()
                            .map(KeycloakRoleDTOV1::getId)
                            .collect(Collectors.toSet());

                    // Requested ids that are not defined on the client.
                    List<String> unknown = requestedRoles.stream()
                            .map(KeycloakRoleDTOV1::getId)
                            .filter(id -> !validIds.contains(id))
                            .toList();

                    // If a single unknown role is present, fail before touching any mapping.
                    if (!unknown.isEmpty()) {
                        return Mono.error(new ExternalServiceException("KEYCLOAK", "replaceUserRoles",
                                "Requested roles are not defined on client " + arBackofficePortalClientClientId + ": " + unknown));
                    }
                    return Mono.empty();
                });
    }

    /**
     * Applies the role change with an add-before-remove ordering, so the user is never stripped of
     * permissions on a failure.
     * <p>
     * The new roles are added first (POST), only once that succeeds are the no-longer-requested
     * roles removed (DELETE). If the POST fails, no removal has happened yet and the user keeps the
     * previous permissions. In the worst case (POST succeeds, DELETE fails) the user temporarily
     * keeps a few extra roles: a safe, additive state recoverable by re-running the operation,
     * rather than being left without permissions. Roles already present are re-POSTed harmlessly.
     *
     * @param adminToken     administrative token
     * @param userId         internal UUID of the user
     * @param internalId     internal UUID of the client
     * @param currentRoles   roles currently assigned to the user
     * @param requestedRoles requested roles
     * @return a {@link Mono} completing when the mutations are done
     */
    private Mono<Void> applyRoleChanges(String adminToken, String userId, String internalId,
            List<KeycloakRoleDTOV1> currentRoles, List<KeycloakRoleDTOV1> requestedRoles) {

        // Set of requested ids, used to compute what must be removed.
        Set<String> requestedIds = requestedRoles.stream()
                .map(KeycloakRoleDTOV1::getId)
                .collect(Collectors.toSet());

        // Current roles not present among the requested ones: these are the ones to remove.
        List<KeycloakRoleDTOV1> toRemove = currentRoles.stream()
                .filter(r -> !requestedIds.contains(r.getId()))
                .toList();

        // Add POST: skipped if there is nothing to add (empty request).
        Mono<Void> addMono = requestedRoles.isEmpty()
                ? Mono.empty()
                : postRoleMapping(adminToken, userId, internalId, requestedRoles);

        // Remove DELETE: skipped if there are no stale roles to remove.
        Mono<Void> removeMono = toRemove.isEmpty()
                ? Mono.empty()
                : deleteRoleMapping(adminToken, userId, internalId, toRemove);

        // Guaranteed ordering: add first, then (only if successful) remove.
        return addMono.then(removeMono);
    }

    // ── Private methods ────────────────────────────────────────────────────────

    /**
     * Resolves the textual {@code clientId} into the internal UUID used by Keycloak.
     * Reuses the in-memory cache if the id was already resolved before.
     *
     * @param adminToken administrative token
     * @return a {@link Mono} with the client's internal UUID
     */
    private Mono<String> getTargetInternalClientId(String adminToken) {
        // Cache hit: avoid a resolution call if the UUID is already known.
        String cached = cachedInternalClientId.get();
        if (cached != null) {
            return Mono.just(cached);
        }

        // Build the URI to look up the client by its textual clientId.
        URI uri = UriComponentsBuilder.fromUriString(authServerUrl)
                .path("/admin/realms/{realm}/clients")
                .queryParam("clientId", arBackofficePortalClientClientId)
                .buildAndExpand(realm)
                .toUri();

        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("findTargetClientInternalId", body)))
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                // Extract the first client found; if the list is empty the client does not exist.
                .flatMap(clients -> clients.stream()
                        .map(c -> (String) c.get("id"))
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new ExternalServiceException("KEYCLOAK", "resolveTargetClient",
                                "Target client not found in Keycloak: " + arBackofficePortalClientClientId))))
                // Populate the cache with the resolved UUID for subsequent calls.
                .doOnSuccess(cachedInternalClientId::set);
    }

    /**
     * Retrieves all roles defined for the given client internal UUID.
     *
     * @param adminToken       administrative token
     * @param internalClientId internal UUID of the client
     * @return a {@link Mono} with the list of the client's roles
     */
    private Mono<List<KeycloakRoleDTOV1>> fetchClientRoles(String adminToken, String internalClientId) {
        return webClient.get()
                .uri(adminUri("/clients/{id}/roles", internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("fetchClientRoles", body)))
                .bodyToMono(new ParameterizedTypeReference<List<KeycloakRoleDTOV1>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork());
    }

    /**
     * Adds the given list of roles to the user's role mapping via POST.
     *
     * @param adminToken       administrative token
     * @param userId           internal UUID of the user
     * @param internalClientId internal UUID of the client
     * @param rolesToAdd       roles to add
     * @return a {@link Mono} completing when the addition is done
     */
    private Mono<Void> postRoleMapping(String adminToken, String userId, String internalClientId, List<KeycloakRoleDTOV1> rolesToAdd) {
        log.info("[AR-BFF][REPLACE_ROLES] Executing POST to add {} new roles on user {}", rolesToAdd.size(), userId);
        return webClient.post()
                .uri(adminUri("/users/{userId}/role-mappings/clients/{clientInternalId}", userId, internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(rolesToAdd)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("assignClientRoles", body)))
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("[AR-BFF][REPLACE_ROLES] New roles successfully added."))
                .then();
    }

    /**
     * Retrieves the roles currently assigned to the user for the specific client.
     *
     * @param adminToken       administrative token
     * @param userId           internal UUID of the user
     * @param internalClientId internal UUID of the client
     * @return a {@link Mono} with the user's current roles on the client
     */
    private Mono<List<KeycloakRoleDTOV1>> getUserClientRoles(String adminToken, String userId, String internalClientId) {
        return webClient.get()
                .uri(adminUri("/users/{userId}/role-mappings/clients/{clientInternalId}", userId, internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("getUserClientRoles", body)))
                .bodyToMono(new ParameterizedTypeReference<List<KeycloakRoleDTOV1>>() {})
                .retryWhen(WebClientRetrySpecs.transientNetwork());
    }

    /**
     * Removes the given list of roles from the user's role mapping via DELETE.
     * <p>
     * Note: {@link WebClient} requires using {@link HttpMethod#DELETE} explicitly
     * to be able to send a request body.
     *
     * @param adminToken       administrative token
     * @param userId           internal UUID of the user
     * @param internalClientId internal UUID of the client
     * @param rolesToDelete    roles to remove
     * @return a {@link Mono} completing when the removal is done
     */
    private Mono<Void> deleteRoleMapping(String adminToken, String userId, String internalClientId, List<KeycloakRoleDTOV1> rolesToDelete) {
        log.info("[AR-BFF][REPLACE_ROLES] Executing DELETE for {} roles on user {}", rolesToDelete.size(), userId);
        return webClient.method(HttpMethod.DELETE)
                .uri(adminUri("/users/{userId}/role-mappings/clients/{clientInternalId}", userId, internalClientId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(rolesToDelete)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> handleKeycloakError("deleteClientRoles", body)))
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("[AR-BFF][REPLACE_ROLES] Old roles successfully removed."))
                .then();
    }

}
