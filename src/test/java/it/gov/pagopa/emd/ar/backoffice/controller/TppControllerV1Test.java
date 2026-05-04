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

/**
 * Unit tests for {@link TppControllerImplV1}.
 * <p>
 * This class tests the REST controller layer in isolation using {@link WebTestClient}.
 * By using the {@code bindToController} approach, the Spring application context is not loaded,
 * resulting in faster test execution while still verifying routing and JSON serialization.
 * </p>
 */
class TppControllerImplV1Test {

    private TppServiceImpl tppService;
    private WebTestClient webTestClient;

    /**
     * Set up the test environment before each test case.
     * Mocks the service layer and manually binds the controller to the WebTestClient.
     */
    @BeforeEach
    void setUp() {
        // Mock the service dependency
        tppService = Mockito.mock(TppServiceImpl.class);
        
        // Instantiate the controller with the mocked service
        TppControllerImplV1 tppController = new TppControllerImplV1(tppService);
        
        // Manually bind the controller to WebTestClient for lightweight unit testing
        webTestClient = WebTestClient.bindToController(tppController).build();
    }

    /**
     * Test case for the health check/routing verification endpoint.
     * <p>
     * Verifies that:
     * <ul>
     *     <li>The HTTP status is 200 OK.</li>
     *     <li>The content type is APPLICATION_JSON.</li>
     *     <li>The response body contains the expected static status and service name.</li>
     * </ul>
     * </p>
     */
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

    /**
     * Test case for the TPP information saving endpoint.
     * <p>
     * Verifies that the controller correctly delegates the creation process
     * to the service layer and returns the generated TPP ID as a response.
     * </p>
     */
    @Test
    void saveTpp_ShouldReturnTppId() {
        // GIVEN: Prepare request data
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Test Tpp Name");
        
        String expectedResponse = "TPP_CREATED_ID_123";

        // WHEN: Define mock behavior for the service call
        when(tppService.createTppAndKeycloakClient(any(TppDTOV1.class)))
                .thenReturn(Mono.just(expectedResponse));

        // THEN: Execute the POST request and verify the response status and body
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