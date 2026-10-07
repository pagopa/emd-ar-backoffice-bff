package it.gov.pagopa.emd.ar.backoffice.connector.citizen;

import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Citizen connector backed by the application's shared WebClient configuration. */
@Slf4j
@Service
public class CitizenConnectorImpl implements CitizenConnector {

    private static final String SEARCH_PATH = "/emd/citizen/search";
    private final WebClient webClient;
    private final String baseUrl;

    public CitizenConnectorImpl(WebClient.Builder webClientBuilder,
                                @Value("${rest.client.citizen.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Mono<CitizenSearchResponse> searchByFiscalCode(String fiscalCode, String cursor, int size) {
        return webClient.get()
                .uri(buildSearchUri(fiscalCode, cursor, size))
                .retrieve()
                .onStatus(status -> status.value() == 400, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ValidationException("Citizen rejected the search request."))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ExternalServiceException(
                                        "CITIZEN_SERVICE", "searchByFiscalCode", "Upstream request failed"))))
                .bodyToMono(CitizenSearchResponse.class)
                .retryWhen(WebClientRetrySpecs.transientNetwork())
                .onErrorMap(error -> !(error instanceof ValidationException)
                                && !(error instanceof ExternalServiceException),
                        error -> new ExternalServiceException(
                                "CITIZEN_SERVICE", "searchByFiscalCode", "Upstream request failed"))
                .doOnError(error -> log.warn("[AR-BFF][CITIZEN_SEARCH] Citizen request failed: {}",
                        error.getClass().getSimpleName()));
    }

    private URI buildSearchUri(String fiscalCode, String cursor, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .path(SEARCH_PATH)
                .queryParam("fiscalCode", "{fiscalCode}")
                .queryParam("size", size);
        Map<String, String> queryValues = new HashMap<>();
        queryValues.put("fiscalCode", fiscalCode);
        if (cursor != null) {
            builder.queryParam("cursor", "{cursor}");
            queryValues.put("cursor", cursor);
        }
        return builder.encode(StandardCharsets.UTF_8).buildAndExpand(queryValues).toUri();
    }
}


