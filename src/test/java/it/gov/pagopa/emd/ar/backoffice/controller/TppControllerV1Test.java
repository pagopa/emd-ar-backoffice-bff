package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.controller.TppControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.tpp.TppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TppControllerImplV1}.
 * <p>
 * This class tests the REST controller layer in isolation using {@link WebTestClient}.
 * By using the {@code bindToController} approach, the Spring application context is not loaded,
 * resulting in faster test execution while still verifying routing and JSON serialization.
 * </p>
 */
class TppControllerImplV1Test {

    private TppService tppService;
    private WebTestClient webTestClient;

    /**
     * Set up the test environment before each test case.
     * Mocks the service layer and manually binds the controller to the WebTestClient.
     */
    @BeforeEach
    void setUp() {
        tppService = Mockito.mock(TppService.class);
        TppControllerImplV1 tppController = new TppControllerImplV1(tppService);
        webTestClient = WebTestClient.bindToController(tppController).build();
    }


    @Test
    void saveTpp_ShouldReturnTppId() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setEntityId("12345678901");
        dto.setBusinessName("Test Tpp Name");
        dto.setAuthenticationType(AuthenticationTypeV1.OAUTH2);
        dto.setAgentLinks(new HashMap<>());

        String expectedTppId = "TPP_CREATED_ID_123";

        when(tppService.createTppAndKeycloakClient(any(TppDTOV1.class)))
                .thenReturn(Mono.just(expectedTppId));

        webTestClient.post()
                .uri("/emd/backoffice/api/v1/tpp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tppId").isEqualTo(expectedTppId);
    }

    /**
     * GET /emd/backoffice/api/v1/tpp?entityId=... — TPP trovata → 200 con tppId.
     */
    @Test
    void getTppByEntityId_Found_Returns200WithTppId() {
        String entityId = "12345678901";
        String expectedTppId = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";

        when(tppService.getTppByEntityId(eq(entityId)))
                .thenReturn(Mono.just(new TppIdResponseDTOV1(expectedTppId)));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp?entityId=" + entityId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.tppId").isEqualTo(expectedTppId);
    }

    /**
     * GET /emd/backoffice/api/v1/tpp?entityId=... — TPP non trovata → il service
     * emette ResourceNotFoundException che il global handler mappa a 404.
     * Il controller non intercetta l'errore — si propaga verso l'alto.
     */
    @Test
    void getTppByEntityId_NotFound_PropagatesError() {
        String entityId = "99999999999";

        when(tppService.getTppByEntityId(eq(entityId)))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp?entityId=" + entityId)
                .exchange()
                .expectStatus().is5xxServerError(); // senza global handler il default è 500
    }

    /**
     * DELETE /emd/backoffice/api/v1/tpp/{tppId} — cancellazione riuscita → 204 No Content.
     */
    @Test
    void deleteTpp_Success_Returns204() {
        String tppId = "tpp-id-to-delete";

        when(tppService.deleteTppAndKeycloakClient(eq(tppId)))
                .thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/emd/backoffice/api/v1/tpp/" + tppId)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    /**
     * DELETE /emd/backoffice/api/v1/tpp/{tppId} — il service emette un errore → si propaga.
     */
    @Test
    void deleteTpp_ServiceError_PropagatesError() {
        String tppId = "tpp-id-failing";

        when(tppService.deleteTppAndKeycloakClient(eq(tppId)))
                .thenReturn(Mono.error(new RuntimeException("Deletion failed")));

        webTestClient.delete()
                .uri("/emd/backoffice/api/v1/tpp/" + tppId)
                .exchange()
                .expectStatus().is5xxServerError();
    }
}

