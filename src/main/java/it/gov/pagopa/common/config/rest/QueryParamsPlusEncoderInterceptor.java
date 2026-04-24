package it.gov.pagopa.common.config.rest;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Filtro per WebClient che codifica il carattere '+' in '%2B' nei query parameters.
 * Necessario perché alcuni sistemi interpretano il '+' come uno spazio.
 */
public class QueryParamsPlusEncoderInterceptor implements ExchangeFilterFunction {

  private static final String PLUS_RAW = "+";
  private static final String PLUS_ENCODED = "%2B";

  @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        URI uri = request.url();
        String escapedQuery = uri.getRawQuery();

        if (escapedQuery != null && escapedQuery.contains(PLUS_RAW)) {
            escapedQuery = escapedQuery.replace(PLUS_RAW, PLUS_ENCODED);
            
            URI newUri = UriComponentsBuilder.fromUri(uri)
                    .replaceQuery(escapedQuery)
                    .build(true) // 'true' indica che i componenti sono già encodati (tranne il nostro +)
                    .toUri();

            // WebClient Request è immutabile, ne creiamo una nuova copia con la nuova URI
            ClientRequest encodedRequest = ClientRequest.from(request)
                    .url(newUri)
                    .build();
            
            return next.exchange(encodedRequest);
        }
        
        return next.exchange(request);
    }
}

