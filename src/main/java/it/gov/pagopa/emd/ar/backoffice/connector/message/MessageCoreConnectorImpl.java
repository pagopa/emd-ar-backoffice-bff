package it.gov.pagopa.emd.ar.backoffice.connector.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageDTOV1;
import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j 
@Service 
public class MessageCoreConnectorImpl implements MessageCoreConnector {
    
    private static final String GET_MESSAGE_PATH = "/emd/message-core/{messageId}";

    private final WebClient webClient;

    public MessageCoreConnectorImpl(WebClient.Builder webClientBuilder,
            @Value ("${rest.client.message.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code GET /emd/message-core/{messageId}} to the remote emd-message-core service.
     * A 404 response is converted to a {@link ResourceNotFoundException} so the BFF
     * can propagate a clean HTTP 404 to the caller. All other errors are wrapped in
     * {@link ExternalServiceException}.</p>
     *
     * <p>Safe to retry with {@link WebClientRetrySpecs#transientNetwork()} — GET is idempotent.</p>
     */
    @Override
    public Mono<MessageDTOV1> getMessageByMessageId(String messageId) {
        return webClient.get()
                .uri(GET_MESSAGE_PATH, messageId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ResourceNotFoundException("MESSAGE", messageId))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new ExternalServiceException("MESSAGE_SERVICE", "getMessageByMessageId", body))))
                .bodyToMono(MessageDTOV1.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .doOnError(ex -> log.error(
                        "[MESSAGE-CONNECTOR] GET {} failed for messageId={}: {}",
                        GET_MESSAGE_PATH, messageId, ex.getMessage()));
    }
}
