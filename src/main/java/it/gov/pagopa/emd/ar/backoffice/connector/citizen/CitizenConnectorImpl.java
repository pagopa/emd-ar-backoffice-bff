package it.gov.pagopa.emd.ar.backoffice.connector.citizen;

import it.gov.pagopa.emd.ar.backoffice.config.WebClientRetrySpecs;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenConsentSearchRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenConsentSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
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
    private static final String CONSENT_SEARCH_PATH = "/emd/citizen/consent/search";
    private static final String TOGGLE_CONSENT_PATH = "/emd/citizen/{fiscalCode}/{tppId}";
    private static final String CONSENT_DELETE_PATH = "/emd/citizen/{fiscalCode}";
    private final WebClient webClient;
    private final String baseUrl;

    public CitizenConnectorImpl(WebClient.Builder webClientBuilder,
                                @Value("${rest.client.citizen.base-url}") String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.webClient = webClientBuilder.baseUrl(this.baseUrl).build();
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

    @Override
    public Mono<CitizenConsentSearchResponse> searchCitizenConsents(String fiscalCode) {
        return webClient.post()
                .uri(CONSENT_SEARCH_PATH)
                .bodyValue(new CitizenConsentSearchRequest(fiscalCode))
                .retrieve()
                .onStatus(status -> status.value() == 400, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ValidationException("Citizen rejected the consent search request."))))
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ResourceNotFoundException("Citizen", "consent"))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ExternalServiceException(
                                        "CITIZEN_SERVICE", "searchCitizenConsents", "Upstream request failed"))))
                .bodyToMono(CitizenConsentSearchResponse.class)
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .onErrorMap(error -> !(error instanceof ValidationException)
                                && !(error instanceof ResourceNotFoundException)
                                && !(error instanceof ExternalServiceException),
                        error -> new ExternalServiceException(
                                "CITIZEN_SERVICE", "searchCitizenConsents", "Upstream request failed"))
                .doOnError(error -> log.warn("[AR-BFF][CITIZEN_CONSENT_SEARCH] Citizen request failed: {}",
                        error.getClass().getSimpleName()));
    }

    @Override
    public Mono<CitizenConsentSearchResponse> toggleCitizenConsent(String fiscalCode, String tppId) {
        return webClient.put()
                .uri(buildConsentToggleUri(fiscalCode, tppId))
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ResourceNotFoundException("Citizen consent", "requested TPP"))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ExternalServiceException(
                                        "CITIZEN_SERVICE", "toggleCitizenConsent", "Upstream request failed"))))
                .bodyToMono(CitizenConsentSearchResponse.class)
                // This endpoint toggles state: even a transport retry could apply the toggle twice.
                .onErrorMap(error -> !(error instanceof ResourceNotFoundException)
                                && !(error instanceof ExternalServiceException),
                        error -> new ExternalServiceException(
                                "CITIZEN_SERVICE", "toggleCitizenConsent", "Upstream request failed"))
                .doOnError(error -> log.warn("[AR-BFF][CITIZEN_CONSENT_TOGGLE] Citizen request failed: {}",
                        error.getClass().getSimpleName()));
    }

    private URI buildConsentToggleUri(String fiscalCode, String tppId) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(TOGGLE_CONSENT_PATH)
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(fiscalCode, tppId)
                .toUri();
    }

    @Override
    public Mono<CitizenConsentSearchResponse> deleteCitizenConsents(String fiscalCode) {
        return webClient.delete()
                .uri(buildConsentDeleteUri(fiscalCode))
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ResourceNotFoundException("Citizen", "consent"))))
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .then(Mono.error(new ExternalServiceException(
                                        "CITIZEN_SERVICE", "deleteCitizenConsents", "Upstream request failed"))))
                .bodyToMono(CitizenConsentSearchResponse.class)
                .retryWhen(WebClientRetrySpecs.connectFailureOnly())
                .onErrorMap(error -> !(error instanceof ResourceNotFoundException)
                                && !(error instanceof ExternalServiceException),
                        error -> new ExternalServiceException(
                                "CITIZEN_SERVICE", "deleteCitizenConsents", "Upstream request failed"))
                .doOnError(error -> log.warn("[AR-BFF][CITIZEN_CONSENT_DELETE] Citizen request failed: {}",
                        error.getClass().getSimpleName()));
    }

    private URI buildConsentDeleteUri(String fiscalCode) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(CONSENT_DELETE_PATH)
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(Map.of("fiscalCode", fiscalCode))
                .toUri();
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

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.replaceAll("/+$", "");
        String citizenPrefix = "/emd/citizen";
        if (normalized.endsWith(citizenPrefix)) {
            normalized = normalized.substring(0, normalized.length() - citizenPrefix.length());
        }
        return normalized;
    }
}
