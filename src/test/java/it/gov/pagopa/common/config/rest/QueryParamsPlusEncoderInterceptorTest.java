package it.gov.pagopa.common.config.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryParamsPlusEncoderInterceptorTest {

    private QueryParamsPlusEncoderInterceptor filter;

    @Mock
    private ExchangeFunction next;
    @Mock
    private ClientResponse mockResponse;

    @BeforeEach
    void setUp() {
        filter = new QueryParamsPlusEncoderInterceptor();
    }

    @Test
    void givenRequestWithPlusWhenFilterThenEncodePlus() {
        // Given
        URI uri = URI.create("http://example/api?datetime=2025-04-08T11:57:03.375%2B02:00");
        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, uri).build();
        
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        when(next.exchange(captor.capture())).thenReturn(Mono.just(mockResponse));

        // When
        Mono<ClientResponse> result = filter.filter(request, next);

        // Then
        StepVerifier.create(result)
                .expectNext(mockResponse)
                .verifyComplete();

        URI interceptedUri = captor.getValue().url();
        // Verifica che il + sia rimasto %2B (o codificato correttamente)
        assertEquals("http://example/api?datetime=2025-04-08T11:57:03.375%2B02:00", interceptedUri.toString());
    }

    @Test
    void givenNullQueryWhenInterceptThenReturnUri() {
        // Given: un URL senza query parameters
        URI uri = URI.create("http://example/api/resource");
        ClientRequest request = ClientRequest.create(HttpMethod.GET, uri).build();
        
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        when(next.exchange(captor.capture())).thenReturn(Mono.just(mockResponse));

        // When
        Mono<ClientResponse> result = filter.filter(request, next);

        // Then
        StepVerifier.create(result)
                .expectNext(mockResponse)
                .verifyComplete();

        // Verifichiamo che l'URI sia rimasto identico
        assertEquals("http://example/api/resource", captor.getValue().url().toString());
    }

}
