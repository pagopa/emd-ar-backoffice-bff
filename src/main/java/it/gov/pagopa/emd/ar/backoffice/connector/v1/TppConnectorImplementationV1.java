package it.gov.pagopa.emd.ar.backoffice.connector.v1;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import reactor.core.publisher.Mono;

/**
 * Implementation of {@link TppConnectorV1} that uses Spring's WebClient to interact with the remote emd-tpp service.</p>
 */
@Service
public class TppConnectorImplementationV1 implements TppConnectorV1{

    /**
     * Configured WebClient pointing to the TPP remote service base URL.</p>
     */
    private final WebClient webClient;

    /**
     * <p>Constructs the connector initializing the WebClient with the provided base URL.</p>
     *
     * @param webClientBuilder Spring-injected builder to create WebClient instances
     * @param baseUrl remote TPP service base URL (property: {@code rest-client.tpp.baseUrl})
     */
    public TppConnectorImplementationV1(WebClient.Builder webClientBuilder,
        @Value("${rest.client.tpp.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Saves TPP data by sending a POST request to the remote service.</p>
     *
     * @param tppDTO the TPP data to save
     * @return {@code Mono<String>} containing the tppId of the saved TPP or an error if the operation fails
     */
    public Mono<String> saveTpp(TppDTOV1 tppDTO) {
        return webClient.post()
                .uri("/emd/tpp/save")
                .bodyValue(tppDTO)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.FORBIDDEN), response ->
                    response.bodyToMono(Map.class).flatMap(errorBody -> {
                        // Extract the business code from the error response body
                        String businessCode = (String) errorBody.get("code");
                        
                        // Return a Mono error with a ResponseStatusException containing the business code as the message
                        return Mono.error(new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            businessCode
                        ));
                    })
                )
                .bodyToMono(TppDTOV1.class)
                .map(TppDTOV1::getTppId);
    }
}
