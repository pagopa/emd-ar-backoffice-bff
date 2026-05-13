package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.controller.TppControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPatchDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.enums.AuthenticationTypeV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.model.ContactV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.tpp.TppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

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
        String entityId = "12345678901";
        TppDTOV1 dto = new TppDTOV1();
        dto.setEntityId(entityId);
        dto.setBusinessName("Test Tpp Name");
        dto.setAuthenticationType(AuthenticationTypeV1.OAUTH2);
        dto.setAgentLinks(new HashMap<>());

        String expectedTppId = "TPP_CREATED_ID_123";

        when(tppService.createTppAndKeycloakClient(eq(entityId), any(TppDTOV1.class)))
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
     * GET /emd/backoffice/api/v1/tpp/{entityId} — TPP trovata → 200 con tutti i campi del DTO.
     */
    @Test
    void getTppByEntityId_Found_Returns200WithFullDto() {
        String entityId = "12345678901";

        TppResponseDTOV1 response = TppResponseDTOV1.builder()
                .tppId("47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176")
                .businessName("My TPP Srl")
                .authenticationType(AuthenticationTypeV1.OAUTH2)
                .contact(new ContactV1("Mario Rossi", "1234567890", "mario@tpp.it"))
                .build();

        when(tppService.getTppByEntityId(eq(entityId)))
                .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.tppId").isEqualTo("47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176")
                .jsonPath("$.businessName").isEqualTo("My TPP Srl")
                .jsonPath("$.authenticationType").isEqualTo("OAUTH2")
                .jsonPath("$.contact.name").isEqualTo("Mario Rossi")
                .jsonPath("$.contact.email").isEqualTo("mario@tpp.it");
    }

    /**
     * GET /emd/backoffice/api/v1/tpp/{entityId} — TPP non trovata → il service
     * emette ResourceNotFoundException che il global handler mappa a 404.
     * Il controller non intercetta l'errore — si propaga verso l'alto.
     */
    @Test
    void getTppByEntityId_NotFound_PropagatesError() {
        String entityId = "99999999999";

        when(tppService.getTppByEntityId(eq(entityId)))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId)
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

    // ── getTppPagopaCredentials ───────────────────────────────────────────────

    /**
     * GET /emd/backoffice/api/v1/tpp/{entityId}/credentials/pagopa — happy path → 200 con clientId e clientSecret.
     */
    @Test
    void getTppPagopaCredentials_Found_Returns200WithCredentials() {
        String entityId = "12345678901";
        String tppId    = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";
        String secret   = "xYz123AbC456DeF789GhI012JkL345Mn";

        when(tppService.getTppPagopaCredentials(eq(entityId)))
                .thenReturn(Mono.just(new TppPagopaCredentialsDTOV1(tppId, secret, "client_credentials")));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials/pagopa")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.clientId").isEqualTo(tppId)
                .jsonPath("$.clientSecret").isEqualTo(secret);
    }

    /**
     * GET .../credentials/pagopa — TPP non trovata su emd-tpp.
     * Il service emette {@link ResourceNotFoundException} che si propaga.
     */
    @Test
    void getTppPagopaCredentials_NotFound_PropagatesError() {
        String entityId = "99999999999";

        when(tppService.getTppPagopaCredentials(eq(entityId)))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials/pagopa")
                .exchange()
                .expectStatus().is5xxServerError(); // senza global handler il default è 500
    }

    /**
     * GET .../credentials/pagopa — Keycloak non raggiungibile.
     * Il service emette {@link ExternalServiceException} (502) che si propaga.
     */
    @Test
    void getTppPagopaCredentials_KeycloakUnavailable_PropagatesError() {
        String entityId = "12345678901";

        when(tppService.getTppPagopaCredentials(eq(entityId)))
                .thenReturn(Mono.error(new ExternalServiceException("KEYCLOAK", "fetchClientSecret", "timeout")));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials/pagopa")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // ── getTppCredentials ─────────────────────────────────────────────────────

    /**
     * GET /emd/backoffice/api/v1/tpp/{entityId}/credentials — happy path →
     * 200 con il token-section completo serializzato in JSON.
     */
    @Test
    void getTppCredentials_Found_Returns200WithTokenSection() {
        String entityId = "12345678901";
        TokenSectionDTOV1 response = new TokenSectionDTOV1(
                "application/json",
                java.util.Map.of("scope", "openid"),
                java.util.Map.of("client_id", "my-client", "client_secret", "s3cr3t"));

        when(tppService.getTppCredentials(eq(entityId)))
                .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.contentType").isEqualTo("application/json")
                .jsonPath("$.pathAdditionalProperties.scope").isEqualTo("openid")
                .jsonPath("$.bodyAdditionalProperties.client_secret").isEqualTo("s3cr3t");
    }

    /**
     * GET .../credentials — TPP non trovata su emd-tpp → errore propagato.
     */
    @Test
    void getTppCredentials_NotFound_PropagatesError() {
        String entityId = "99999999999";

        when(tppService.getTppCredentials(eq(entityId)))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    /**
     * GET .../credentials — emd-tpp non raggiungibile → {@link ExternalServiceException} propagata.
     */
    @Test
    void getTppCredentials_ServiceUnavailable_PropagatesError() {
        String entityId = "12345678901";

        when(tppService.getTppCredentials(eq(entityId)))
                .thenReturn(Mono.error(new ExternalServiceException("TPP_SERVICE", "getTppToken", "timeout")));

        webTestClient.get()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // ── updateTppCredentials ──────────────────────────────────────────────────

    /**
     * PUT /emd/backoffice/api/v1/tpp/{entityId}/credentials — happy path → 200 OK con body.
     */
    @Test
    void updateTppCredentials_Success_Returns200WithBody() {
        String entityId = "12345678901";
        TokenSectionDTOV1 body = new TokenSectionDTOV1(
                "application/x-www-form-urlencoded",
                Map.of("tenantId", "123456"),
                Map.of("client_id", "nuovo-client", "client_secret", "nuovo-secret"));

        when(tppService.updateTppCredentials(eq(entityId), any(TokenSectionDTOV1.class)))
                .thenReturn(Mono.just(body));

        webTestClient.put()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.contentType").isEqualTo("application/x-www-form-urlencoded");
    }

    /**
     * PUT .../credentials — TPP non trovata su emd-tpp → errore 404 propagato.
     */
    @Test
    void updateTppCredentials_TppNotFound_PropagatesError() {
        String entityId = "99999999999";
        TokenSectionDTOV1 body = new TokenSectionDTOV1("application/json", null, null);

        when(tppService.updateTppCredentials(eq(entityId), any(TokenSectionDTOV1.class)))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        webTestClient.put()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    /**
     * PUT .../credentials — emd-tpp non raggiungibile → {@link ExternalServiceException} propagata.
     */
    @Test
    void updateTppCredentials_ServiceUnavailable_PropagatesError() {
        String entityId = "12345678901";
        TokenSectionDTOV1 body = new TokenSectionDTOV1("application/json", null, null);

        when(tppService.updateTppCredentials(eq(entityId), any(TokenSectionDTOV1.class)))
                .thenReturn(Mono.error(new ExternalServiceException("TPP_SERVICE", "updateTppToken", "timeout")));

        webTestClient.put()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId + "/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // ── patchTpp ──────────────────────────────────────────────────────────────

    /**
     * PATCH /emd/backoffice/api/v1/tpp/{entityId} — happy path → 200 OK con
     * la rappresentazione completa della TPP aggiornata.
     */
    @Test
    void patchTpp_Success_Returns200WithUpdatedTpp() {
        String entityId = "12345678901";

        TppPatchDTOV1 patchBody = TppPatchDTOV1.builder()
                .messageUrl("https://api.acme.com/v2/messages")
                .contact(new ContactV1("Luigi Bianchi", "0612345678", "nuovo-tech@acme.com"))
                .build();

        TppResponseDTOV1 updatedResponse = TppResponseDTOV1.builder()
                .tppId("47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176")
                .businessName("Acme TPP S.p.A.")
                .messageUrl("https://api.acme.com/v2/messages")
                .contact(new ContactV1("Luigi Bianchi", "0612345678", "nuovo-tech@acme.com"))
                .build();

        when(tppService.patchTpp(eq(entityId), any(TppPatchDTOV1.class)))
                .thenReturn(Mono.just(updatedResponse));

        webTestClient.patch()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patchBody)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBody()
                .jsonPath("$.tppId").isEqualTo("47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176")
                .jsonPath("$.businessName").isEqualTo("Acme TPP S.p.A.")
                .jsonPath("$.messageUrl").isEqualTo("https://api.acme.com/v2/messages")
                .jsonPath("$.contact.name").isEqualTo("Luigi Bianchi")
                .jsonPath("$.contact.email").isEqualTo("nuovo-tech@acme.com");
    }

    /**
     * PATCH .../tpp/{entityId} — TPP non trovata su emd-tpp → errore propagato.
     */
    @Test
    void patchTpp_TppNotFound_PropagatesError() {
        String entityId = "99999999999";
        TppPatchDTOV1 patchBody = TppPatchDTOV1.builder().businessName("New Name").build();

        when(tppService.patchTpp(eq(entityId), any(TppPatchDTOV1.class)))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        webTestClient.patch()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patchBody)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    /**
     * PATCH .../tpp/{entityId} — emd-tpp non raggiungibile → {@link ExternalServiceException} propagata.
     */
    @Test
    void patchTpp_ServiceUnavailable_PropagatesError() {
        String entityId = "12345678901";
        TppPatchDTOV1 patchBody = TppPatchDTOV1.builder().businessName("New Name").build();

        when(tppService.patchTpp(eq(entityId), any(TppPatchDTOV1.class)))
                .thenReturn(Mono.error(new ExternalServiceException("TPP_SERVICE", "patchTpp", "timeout")));

        webTestClient.patch()
                .uri("/emd/backoffice/api/v1/tpp/" + entityId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patchBody)
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
