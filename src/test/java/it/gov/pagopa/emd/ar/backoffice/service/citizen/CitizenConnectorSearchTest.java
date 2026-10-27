package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.connector.citizen.CitizenConnectorImpl;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class CitizenConnectorSearchTest {

    private static final String BASE_URL = "http://emd-citizen.test";

    @Test
    void firstPageOmitsCursorAndDeserializesAllResponseFields() {
        String json = """
                {
                  "content": [{"fiscalCode":"RSS11111L04L741X","consentCount":1}],
                  "pageSize":10,
                  "totalElements":34,
                  "totalPages":4,
                  "nextCursor":"RSS11111L04L741X",
                  "hasNext":true
                }
                """;
        String[] capturedUrl = new String[1];
        CitizenConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(jsonResponse(HttpStatus.OK, json));
        });

        StepVerifier.create(connector.searchByFiscalCode("RSS11111L04L741X", null, 10))
                .assertNext(response -> {
                    assertThat(response.getContent()).hasSize(1);
                    assertThat(response.getContent().getFirst().getFiscalCode()).isEqualTo("RSS11111L04L741X");
                    assertThat(response.getContent().getFirst().getConsentCount()).isEqualTo(1);
                    assertThat(response.getPageSize()).isEqualTo(10);
                    assertThat(response.getTotalElements()).isEqualTo(34);
                    assertThat(response.getTotalPages()).isEqualTo(4);
                    assertThat(response.getNextCursor()).isEqualTo("RSS11111L04L741X");
                    assertThat(response.isHasNext()).isTrue();
                })
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("/emd/citizen/search?");
        assertThat(capturedUrl[0]).contains("fiscalCode=RSS11111L04L741X");
        assertThat(capturedUrl[0]).contains("size=10");
        assertThat(capturedUrl[0]).doesNotContain("cursor=").doesNotContain("page=");
    }

    @Test
    void nextCursorIsForwardedAndQueryValuesAreEncoded() {
        String[] capturedUrl = new String[1];
        CitizenConnectorImpl connector = connectorWith(request -> {
            capturedUrl[0] = request.url().toString();
            return Mono.just(jsonResponse(HttpStatus.OK,
                    "{\"content\":[],\"pageSize\":10,\"totalElements\":0,\"totalPages\":0,\"nextCursor\":null,\"hasNext\":false}"));
        });

        StepVerifier.create(connector.searchByFiscalCode("ABC+DEF", "ABC+DEF 12345678", 10))
                .assertNext(response -> {
                    assertThat(response.getContent()).isEmpty();
                    assertThat(response.getNextCursor()).isNull();
                    assertThat(response.isHasNext()).isFalse();
                })
                .verifyComplete();

        assertThat(capturedUrl[0]).contains("fiscalCode=ABC%2BDEF");
        assertThat(capturedUrl[0]).contains("cursor=ABC%2BDEF%2012345678");
    }

    @Test
    void citizenBadRequestMapsToStandardValidationError() {
        CitizenConnectorImpl connector = connectorWith(request ->
                Mono.just(jsonResponse(HttpStatus.BAD_REQUEST, "{\"detail\":\"private upstream detail\"}")));

        StepVerifier.create(connector.searchByFiscalCode("ABC", null, 10))
                .expectErrorMatches(error -> error instanceof ValidationException
                        && !error.getMessage().contains("private upstream detail"))
                .verify();
    }

    @Test
    void citizenServerErrorMapsToExternalServiceExceptionWithoutUpstreamBody() {
        CitizenConnectorImpl connector = connectorWith(request ->
                Mono.just(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "private server detail")));

        StepVerifier.create(connector.searchByFiscalCode("ABC", null, 10))
                .expectErrorMatches(error -> error instanceof ExternalServiceException
                        && !error.getMessage().contains("private server detail"))
                .verify();
    }

    @Test
    void malformedCitizenResponseMapsToExternalServiceException() {
        CitizenConnectorImpl connector = connectorWith(request ->
                Mono.just(jsonResponse(HttpStatus.OK, "not-json")));

        StepVerifier.create(connector.searchByFiscalCode("ABC", null, 10))
                .expectError(ExternalServiceException.class)
                .verify();
    }

    private static ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static CitizenConnectorImpl connectorWith(ExchangeFunction exchangeFunction) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        return new CitizenConnectorImpl(builder, BASE_URL);
    }
}

