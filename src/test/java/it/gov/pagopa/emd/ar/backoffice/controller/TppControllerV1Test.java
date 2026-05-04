package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.controller.v1.TppControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.TppServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TppControllerImplV1Test {

    private TppServiceImpl tppService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // Mockiamo il servizio
        tppService = Mockito.mock(TppServiceImpl.class);
        
        // Creiamo l'istanza del controller passandogli il mock
        TppControllerImplV1 tppController = new TppControllerImplV1(tppService);
        
        // Bindiamo il controller manualmente al WebTestClient
        webTestClient = WebTestClient.bindToController(tppController).build();
    }

    @Test
    void test_ShouldReturnHealthStatus() {
        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/test")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("OK")
                .jsonPath("$.service").isEqualTo("emd-ar-backoffice-bff");
    }

    @Test
    void saveTpp_ShouldReturnTppId() {
        // Preparazione dati
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Test Tpp Name");
        
        String expectedResponse = "TPP_CREATED_ID_123";

        // Definizione comportamento del mock
        when(tppService.createTppAndKeycloakClient(any(TppDTOV1.class)))
                .thenReturn(Mono.just(expectedResponse));

        // Esecuzione e verifica
        webTestClient.post()
                .uri("/emd/backoffice/api/v1/tpp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(expectedResponse);
    }
}