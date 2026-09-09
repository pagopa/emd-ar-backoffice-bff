package it.gov.pagopa.emd.ar.backoffice.connector.message;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j 
@Service 
public class MessageCoreConnectorImpl implements MessageCoreConnector {
    
    private static final String SEARCH_MESSAGE_PATH     = "/emd/message-core/search";

    private final WebClient webClient;

    public MessageCoreConnectorImpl(WebClient.Builder webClientBuilder,
            @Value ("${rest.client.message.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code GET /emd/message-core/search} to the remote emd-message service, adding only
     * non-null / non-blank query parameters to the URI. The {@code fields} list, when
     * non-empty, is added as multiple {@code fields=X} query parameters. The full paginated
     * {@link MessageSearchResponseDTOV1} is returned on success.</p>
     *
     * <p>HTTP 400 with body {@code INVALID_SEARCH_FIELD} is mapped to
     * {@link InvalidSearchFieldException}; all other errors become
     * {@link ExternalServiceException}.</p>
     *
     * <p>GET is idempotent, so transient retries are safe via
     * {@link WebClientRetrySpecs#transientNetwork()}.</p>
     */
    @Override
    public Mono<MessageSearchResponseDTOV1> searchMessages(String messageId, String recipientId, String originId, LocalDateTime startDate, LocalDateTime endDate, int page, int size, List<String> fields) {
        String joinedFields = fields != null ? String.join(",", fields) : "";
        int fieldCount     = fields != null ? fields.size() : 0;
        return webClient.get()
                .uri(uriBuilder -> buildSearchUri(uriBuilder, messageId, recipientId, originId, startDate, endDate, page, size, fields))
                .retrieve()
                .onStatus(status -> status.value() == 400, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new InvalidSearchFieldException(joinedFields, body))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("MESSAGE_SERVICE", "searchMessages", body))))
                .bodyToMono(MessageSearchResponseDTOV1.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .doOnError(ex -> log.error(
                        "[MESSAGE-CONNECTOR] GET {} failed (messageId={}, recipientId={}, originId={}, fields={}): {}",
                        SEARCH_MESSAGE_PATH,
                        messageId != null ? "***" : null,
                        recipientId != null ? "***" : null,
                        originId != null ? "***" : null,
                        fieldCount,
                        ex.getMessage()));
    }

    /**
     * Builds the URI for {@code GET /emd/message-core/search}, appending only the query parameters
     * that are actually provided (non-null / non-blank). Extracted to keep the cognitive
     * complexity of {@link #searchMessages} within the allowed threshold.
     */
    private URI buildSearchUri(UriBuilder uriBuilder, String messageId, String recipientId, String originId,
                                LocalDateTime startDate, LocalDateTime endDate, int page, int size, List<String> fields) {
        uriBuilder.path(SEARCH_MESSAGE_PATH)
                    .queryParam("page", page)
                    .queryParam("size", size);
        if (messageId != null && !messageId.isBlank()) {
            uriBuilder.queryParam("messageId", messageId);
        }
        if (recipientId != null && !recipientId.isBlank()) {
            uriBuilder.queryParam("recipientId", recipientId);
        }
        if (originId != null && !originId.isBlank()) {
            uriBuilder.queryParam("originId", originId);
        }
        if (startDate != null) {
            uriBuilder.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            uriBuilder.queryParam("endDate", endDate);
        }
        if (fields != null && !fields.isEmpty()) {
            fields.forEach(f -> uriBuilder.queryParam("fields", f));
        }
        return uriBuilder.build();
    }
}
