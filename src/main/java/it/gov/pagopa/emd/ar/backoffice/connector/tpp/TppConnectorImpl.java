package it.gov.pagopa.emd.ar.backoffice.connector.tpp;

import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.SaveTppResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.TppAlreadyOnboardedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    private static final String SAVE_TPP_PATH = "/emd/tpp/save";
    private static final String DELETE_TPP_PATH = "/emd/tpp/{tppId}";
    private static final String GET_TPP_BY_ENTITY_ID_PATH = "/emd/tpp/entityId/{entityId}";
    private static final String GET_TPP_TOKEN_PATH = "/emd/tpp/{tppId}/token";

    private final WebClient webClient;

    public TppConnectorImpl(WebClient.Builder webClientBuilder,
            @Value("${rest.client.tpp.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serializes the {@link TppCreateRequest} as the POST body,
     * then extracts the {@code tppId} from the {@link SaveTppResponse}.</p>
     *
     * <p>Retries up to {@value WebClientRetrySpecs#MAX_RETRY_ATTEMPTS} times on
     * TCP connect failures (safe for POST — request never reached the server).</p>
     */
    @Override
    public Mono<String> saveTpp(TppCreateRequest request) {
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
                .bodyToMono(SaveTppResponse.class)
                .map(SaveTppResponse::tppId)
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
}
