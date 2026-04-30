package it.gov.pagopa.emd.ar.backoffice.connector.v1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import reactor.core.publisher.Mono;

@Service
public class TppConnectorImplementationV1 {
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
     * <p>Saves TPP data by sending a POST request to the remote service.</p>
     * @param tppDTO
     * @return
     */
    public Mono<String> saveTpp(TppDTOV1 tppDTO) {
        return webClient.post()
                .uri("/save")
                .bodyValue(tppDTO)
                .retrieve()
                .bodyToMono(TppDTOV1.class)
                .map(TppDTOV1::getTppId);
    }
}
