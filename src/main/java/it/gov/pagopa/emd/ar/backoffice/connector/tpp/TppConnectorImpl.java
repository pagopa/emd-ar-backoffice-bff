package it.gov.pagopa.emd.ar.backoffice.connector.tpp;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateIsPaymentEnabledDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppUpdateStateDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.RecipientIdOnWhitelistDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppConnectionResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppPatchRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientAlreadyPresentException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.RecipientNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.TppAlreadyOnboardedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

import org.springframework.web.util.UriBuilder;

import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.TimeoutException;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Implementation of {@link TppConnector} that uses Spring's WebClient
 * to interact with the remote emd-tpp service.
 *
 * <p>The injected {@link WebClient.Builder} is a <em>prototype</em> bean pre-wired
 * with the application-wide {@link reactor.netty.http.client.HttpClient} (including
 * connect / read / write / response timeouts and the shared connection pool).
 *
 * <p>Retry strategy: POST is non-idempotent, so only TCP-handshake failures are
 * retried via {@link WebClientRetrySpecs#connectFailureOnly()}. This guarantees
 * that the request is never delivered twice.
 */
@Slf4j
@Service
public class TppConnectorImpl implements TppConnector {

    private static final String SAVE_TPP_PATH             = "/emd/tpp/save";
    private static final String DELETE_TPP_PATH           = "/emd/tpp/{tppId}";
    private static final String PATCH_TPP_PATH            = "/emd/tpp/{tppId}";
    private static final String GET_TPP_BY_ENTITY_ID_PATH = "/emd/tpp/entityId/{entityId}";
    private static final String GET_TPP_TOKEN_PATH        = "/emd/tpp/{tppId}/token";
    private static final String UPDATE_TPP_TOKEN_PATH     = "/emd/tpp/update/{tppId}/token";
    private static final String SEARCH_TPP_PATH           = "/emd/tpp/search";
    private static final String UPDATE_TPP_STATE_PATH     = "/emd/tpp";
    private static final String UPDATE_TPP_ISPAYMENT_PATH = "/emd/tpp/{tppId}/payment-enabled";
    private static final String ADD_RECIPIENT_ID_ON_WHITELIST_PATH = "/emd/tpp/{tppId}/whitelist";
    private static final String DELETE_RECIPIENT_ID_FROM_WHITELIST_PATH = "/emd/tpp/{tppId}/whitelist/{recipientId}";
    private static final String UPDATE_RECIPIENT_ID_ON_WHITELIST_PATH = "/emd/tpp/{tppId}/whitelist";
    private static final String TPP_CONNECTION_TEST_PATH  = "/emd/tpp/{tppId}/network/connection/test";

    private final WebClient webClient;

    public TppConnectorImpl(WebClient.Builder webClientBuilder,
            @Value("${rest.client.tpp.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serializes the {@link TppCreateRequest} as the POST body,
     * then deserializes the full {@link TppEntityIdResponse} returned by the upstream service.</p>
     *
     * <p>Retries up to {@value WebClientRetrySpecs#MAX_RETRY_ATTEMPTS} times on
     * TCP connect failures (safe for POST — request never reached the server).</p>
     */
    @Override
    public Mono<TppEntityIdResponse> saveTpp(TppCreateRequest request) {
        return webClient.post()
                .uri(SAVE_TPP_PATH)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.value() == 409, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new TppAlreadyOnboardedException(body))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("TPP_SERVICE", "saveTpp", body))))
                .bodyToMono(TppEntityIdResponse.class)
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] POST {} failed: {}", SAVE_TPP_PATH, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retries up to {@value WebClientRetrySpecs#MAX_RETRY_ATTEMPTS} times on
     * TCP connect failures (safe for DELETE — the server-side operation is idempotent).</p>
     */
    @Override
    public Mono<Void> deleteTpp(String tppId) {
        return webClient.delete()
                .uri(DELETE_TPP_PATH, tppId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("TPP_SERVICE", "deleteTpp", body))))
                .bodyToMono(Void.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] DELETE {} failed for tppId={}: {}", DELETE_TPP_PATH, tppId, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code GET /emd/tpp/entityId/{entityId}} to the remote emd-tpp service.
     * A 404 response is converted to a {@link ResourceNotFoundException} so the BFF
     * can propagate a clean HTTP 404 to the caller. All other errors are wrapped in
     * {@link ExternalServiceException}.</p>
     *
     * <p>Safe to retry with {@link WebClientRetrySpecs#transientNetwork()} — GET is idempotent.</p>
     */
    @Override
    public Mono<TppEntityIdResponse> getTppByEntityId(String entityId) {
        return webClient.get()
                .uri(GET_TPP_BY_ENTITY_ID_PATH, entityId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ResourceNotFoundException("TPP", entityId))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("TPP_SERVICE", "getTppByEntityId", body))))
                .bodyToMono(TppEntityIdResponse.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] GET {} failed for entityId={}: {}",
                        GET_TPP_BY_ENTITY_ID_PATH, entityId, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code GET /emd/tpp/{tppId}/token} to the remote emd-tpp service.
     * A 404 response is converted to a {@link ResourceNotFoundException}.
     * All other errors are wrapped in {@link ExternalServiceException}.</p>
     *
     * <p>Safe to retry with {@link WebClientRetrySpecs#transientNetwork()} — GET is idempotent.</p>
     */
    @Override
    public Mono<TokenSection> getTppToken(String tppId) {
        return webClient.get()
                .uri(GET_TPP_TOKEN_PATH, tppId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ResourceNotFoundException("TPP token", tppId))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("TPP_SERVICE", "getTppToken", body))))
                .bodyToMono(TokenSection.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] GET {} failed for tppId={}: {}",
                        GET_TPP_TOKEN_PATH, tppId, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code PUT /emd/tpp/update/{tppId}/token} to the remote emd-tpp service with
     * the new {@link TokenSection} as JSON body. The response body (which mirrors the request)
     * is deserialized and returned to the caller.</p>
     *
     * <p><strong>Privacy:</strong> the body is deliberately never logged (may contain secrets).</p>
     *
     * <p>PUT is idempotent, so transient retries are safe via
     * {@link WebClientRetrySpecs#transientNetwork()}.</p>
     */
    @Override
    public Mono<TokenSection> updateTppToken(String tppId, TokenSection tokenSection) {
        return webClient.put()
                .uri(UPDATE_TPP_TOKEN_PATH, tppId)
                .bodyValue(tokenSection)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ResourceNotFoundException("TPP token", tppId))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("TPP_SERVICE", "updateTppToken", body))))
                .bodyToMono(TokenSection.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] PUT {} failed for tppId={}: {}",
                        UPDATE_TPP_TOKEN_PATH, tppId, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code PATCH /emd/tpp/{tppId}} to the remote emd-tpp service with the
     * partial {@link TppPatchRequest} as JSON body (null fields are omitted via
     * {@code @JsonInclude(NON_NULL)}). The full, updated {@link TppEntityIdResponse} is
     * returned on success.</p>
     *
     * <p>PATCH is idempotent here, so transient retries are safe via
     * {@link WebClientRetrySpecs#transientNetwork()}.</p>
     */
    @Override
    public Mono<TppEntityIdResponse> patchTpp(String tppId, TppPatchRequest patchRequest) {
        return webClient.patch()
                .uri(PATCH_TPP_PATH, tppId)
                .bodyValue(patchRequest)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ResourceNotFoundException("TPP", tppId))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("TPP_SERVICE", "patchTpp", body))))
                .bodyToMono(TppEntityIdResponse.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                 .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] PATCH {} failed for tppId={}: {}",
                        PATCH_TPP_PATH, tppId, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code GET /emd/tpp/search} to the remote emd-tpp service, adding only
     * non-null / non-blank query parameters to the URI. The {@code fields} list, when
     * non-empty, is added as multiple {@code fields=X} query parameters. The full paginated
     * {@link TppSearchResponse} is returned on success.</p>
     *
     * <p>HTTP 400 with body {@code INVALID_SEARCH_FIELD} is mapped to
     * {@link InvalidSearchFieldException}; all other errors become
     * {@link ExternalServiceException}.</p>
     *
     * <p>GET is idempotent, so transient retries are safe via
     * {@link WebClientRetrySpecs#transientNetwork()}.</p>
     */
    @Override
    public Mono<TppSearchResponse> searchTpp(String entityId, String businessName, int page, int size, List<String> fields) {
        String joinedFields = fields != null ? String.join(",", fields) : "";
        int fieldCount     = fields != null ? fields.size() : 0;
        return webClient.get()
                .uri(uriBuilder -> buildSearchUri(uriBuilder, entityId, businessName, page, size, fields))
                .retrieve()
                .onStatus(status -> status.value() == 400, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new InvalidSearchFieldException(joinedFields, body))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("TPP_SERVICE", "searchTpp", body))))
                .bodyToMono(TppSearchResponse.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] GET {} failed (entityId={}, businessName={}, fields={}): {}",
                        SEARCH_TPP_PATH,
                        entityId != null ? "***" : null,
                        businessName != null ? "***" : null,
                        fieldCount,
                        ex.getMessage()));
    }

    /**
     * Builds the URI for {@code GET /emd/tpp/search}, appending only the query parameters
     * that are actually provided (non-null / non-blank). Extracted to keep the cognitive
     * complexity of {@link #searchTpp} within the allowed threshold.
     */
    private URI buildSearchUri(UriBuilder uriBuilder, String entityId, String businessName,
                                int page, int size, List<String> fields) {
        uriBuilder.path(SEARCH_TPP_PATH)
                    .queryParam("page", page)
                    .queryParam("size", size);
        if (entityId != null && !entityId.isBlank()) {
            uriBuilder.queryParam("entityId", entityId);
        }
        if (businessName != null && !businessName.isBlank()) {
            uriBuilder.queryParam("businessName", businessName);
        }
        if (fields != null && !fields.isEmpty()) {
            fields.forEach(f -> uriBuilder.queryParam("fields", f));
        }
        return uriBuilder.build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code PUT /emd/tpp} to the remote service.
     * Safe to retry with {@link WebClientRetrySpecs#transientNetwork()} as PUT is idempotent.</p>
     */
    @Override
    public Mono<TppEntityIdResponse> updateTppState(TppUpdateStateDTOV1 tppUpdateStateDTO) {
        return webClient.put()
                .uri(UPDATE_TPP_STATE_PATH)
                .bodyValue(tppUpdateStateDTO)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        Mono.error(new ResourceNotFoundException("TPP", tppUpdateStateDTO.getTppId())))
                .bodyToMono(TppEntityIdResponse.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .onErrorMap(
                    WebClientResponseException.class,
                    ex -> new ExternalServiceException("TPP_SERVICE", "updateTppState", ex.getResponseBodyAsString())
                )
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] PUT {} failed for tppId={}: {}",
                        UPDATE_TPP_STATE_PATH, tppUpdateStateDTO.getTppId(), ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code PUT /emd/tpp/{tppId}/payment-enabled} to the remote service.
     * Safe to retry with {@link WebClientRetrySpecs#transientNetwork()}.</p>
     */
    @Override
    public Mono<Void> updateTppIsPaymentEnabled(String tppId, TppUpdateIsPaymentEnabledDTOV1 tppUpdateIsPaymentEnabledDTO) {
        return webClient.put()
                .uri(UPDATE_TPP_ISPAYMENT_PATH, tppId)
                .bodyValue(tppUpdateIsPaymentEnabledDTO)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        Mono.error(new ResourceNotFoundException("TPP", tppId)))
                .toBodilessEntity()
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .onErrorMap(
                    WebClientResponseException.class,
                    ex -> new ExternalServiceException("TPP_SERVICE","updateTppIsPaymentEnabled",ex.getResponseBodyAsString())
                )
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] PUT {} failed for tppId={}: {}",
                        UPDATE_TPP_ISPAYMENT_PATH, tppId, ex.getMessage()))
                .then();
    }
     
     /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> insertRecipientIdOnWhitelist(String tppId, RecipientIdOnWhitelistDTOV1 recipientIdOnWhitelistDTO) {
        return webClient.post()
                .uri(ADD_RECIPIENT_ID_ON_WHITELIST_PATH, tppId)
                .bodyValue(recipientIdOnWhitelistDTO)
                .retrieve()
                .onStatus(status -> status.value() == 409, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RecipientAlreadyPresentException("Recipient already present in whitelist"))))
                .onStatus(status -> status.value() == 404, response ->
                        Mono.error(new ResourceNotFoundException("TPP", tppId)))
                .bodyToMono(Void.class)
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .onErrorMap(
                    WebClientResponseException.class,
                    ex -> new ExternalServiceException("TPP_SERVICE", "insertRecipientIdOnWhitelist", ex.getResponseBodyAsString())
                )
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] POST {} failed: {}", ADD_RECIPIENT_ID_ON_WHITELIST_PATH, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> removeRecipientIdOnWhitelist(String tppId, String recipientId) {
        return webClient.delete()
                .uri(DELETE_RECIPIENT_ID_FROM_WHITELIST_PATH, tppId, recipientId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("") //Prevents generic errors if the server returns an empty body
                                .flatMap(body -> {
                                    if (body.contains("TPP_NOT_ONBOARDED")) {
                                        return Mono.error(new ResourceNotFoundException("TPP", tppId));
                                    } else if (body.contains("RECIPIENT_NOT_FOUND")) {
                                        return Mono.error(new RecipientNotFoundException("Recipient not found in whitelist"));
                                    } else {
                                        // Generic fallback for any other 404 reason, including empty body
                                        return Mono.error(new ResourceNotFoundException("Whitelist Element", recipientId));
                                    }
                                })
                )
                .bodyToMono(Void.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .onErrorMap(
                    WebClientResponseException.class,
                    ex -> new ExternalServiceException("TPP_SERVICE", "removeRecipientIdOnWhitelist", ex.getResponseBodyAsString())
                )
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] DELETE {} failed for tppId={}: {}", DELETE_RECIPIENT_ID_FROM_WHITELIST_PATH, tppId, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> updateRecipientIdOnWhitelist(String tppId, List<String> recipientIds) {
        return webClient.put()
                .uri(UPDATE_RECIPIENT_ID_ON_WHITELIST_PATH, tppId)
                .bodyValue(recipientIds)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        Mono.error(new ResourceNotFoundException("TPP", tppId)))
                .bodyToMono(Void.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .onErrorMap(
                    WebClientResponseException.class,
                    ex -> new ExternalServiceException("TPP_SERVICE", "updateRecipientIdOnWhitelist", ex.getResponseBodyAsString())
                )
                .doOnError(ex -> log.error(
                        "[TPP-CONNECTOR] PUT {} failed for tppId={}: {}", UPDATE_RECIPIENT_ID_ON_WHITELIST_PATH, tppId, ex.getMessage()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code GET /emd/tpp/{tppId}/network/connection/test} to the remote emd-tpp service.
     * The response is deserialized into a structured DTO.</p>
     */
    @Override
    public Mono<TppConnectionResponseDTOV1> testAuthConnection(String tppId) {
        log.info("[TPP-CONNECTOR] Initiating connection test request for tppId={}", tppId);
        return webClient.get()
                .uri(TPP_CONNECTION_TEST_PATH, tppId)
                .retrieve()
                .bodyToMono(TppConnectionResponseDTOV1.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    TppConnectionResponseDTOV1.TppConnectionResponseDTOV1Builder builder = TppConnectionResponseDTOV1.builder()
                            .status("FAILURE");

                    // Gestione specifica per i TIMEOUT di Netty
                    if (e instanceof ReadTimeoutException || e instanceof java.util.concurrent.TimeoutException ||e instanceof TimeoutException ||
                        (e instanceof WebClientRequestException && e.getCause() instanceof ReadTimeoutException)) {
                        
                        builder.errorType("TIMEOUT")
                            .httpStatus(504)
                            .description("Request timed out after the configured limit (ReadTimeout).");
                    } 
                    // Gestione errori HTTP (4xx, 5xx)
                    else if (e instanceof WebClientResponseException we) {
                        builder.errorType("HTTP_ERROR")
                            .httpStatus(we.getStatusCode().value())
                            .description("Upstream service returned an error: " + we.getStatusText());
                    }
                    // Altri errori (Connessione rifiutata, DNS)
                    else {
                        String errorMessage = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
                        builder.errorType("NETWORK_ERROR")
                            .description("Network error: " + errorMessage);
                    }
                    return Mono.just(builder.build());
                })
                .doOnNext(r -> log.info("[TPP-CONNECTOR] Connection test completed for tppId={} with status: {}", 
                        tppId, r.getStatus()));
        }
}
