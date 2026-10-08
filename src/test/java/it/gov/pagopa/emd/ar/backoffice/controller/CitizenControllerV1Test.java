package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.controller.CitizenControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.citizen.CitizenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CitizenControllerV1Test {

    private CitizenService citizenService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        citizenService = Mockito.mock(CitizenService.class);
        CitizenControllerImplV1 controller = new CitizenControllerImplV1(citizenService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void searchDelegatesRequiredCodeAndOptionalParametersToService() {
        when(citizenService.searchByFiscalCode(eq("rss11111l04l741x"), isNull(), isNull()))
                .thenReturn(Mono.just(CitizenSearchResponseDTOV1.builder()
                        .content(java.util.List.of())
                        .pageSize(10)
                        .totalElements(0)
                        .totalPages(0)
                        .nextCursor(null)
                        .hasNext(false)
                        .build()));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/emd/backoffice/api/v1/citizen/search")
                        .queryParam("fiscalCode", "rss11111l04l741x")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.pageSize").isEqualTo(10)
                .jsonPath("$.nextCursor").isEmpty()
                .jsonPath("$.hasNext").isEqualTo(false);

        verify(citizenService).searchByFiscalCode("rss11111l04l741x", null, null);
    }

    @Test
    void missingFiscalCodeIsRejectedAsBadRequest() {
        webTestClient.get()
                .uri("/emd/backoffice/api/v1/citizen/search")
                .exchange()
                .expectStatus().isBadRequest();
    }
}

