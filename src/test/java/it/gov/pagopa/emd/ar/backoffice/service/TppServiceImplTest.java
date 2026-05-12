package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppCreateRequest;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ResourceNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakClientService;
import it.gov.pagopa.emd.ar.backoffice.service.tpp.TppServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Map;

/**
 * Unit tests for {@link TppServiceImpl}.
 * <p>
 * Validates the service orchestration: TPP persistence happens first (obtaining the tppId),
 * followed by Keycloak client creation using that tppId as the client identifier.
 * The mapper (TppConnectorMapper) is NOT mocked — it is pure logic with no side effects.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class TppServiceImplTest {

    @Mock
    private TppConnector tppConnector;

    @Mock
    private KeycloakClientService keycloakClientService;

    private TppServiceImpl tppService;

    @BeforeEach
    void setUp() {
        tppService = new TppServiceImpl(tppConnector, keycloakClientService);
    }

    /**
     * Happy path: the mapper converts the API DTO to a TppCreateRequest,
     * the connector persists it and returns a tppId, then the KC client is created
     * with both tppId (as clientId) and entityId for the hardcoded-claim mapper.
     */
    @Test
    void createTppAndKeycloakClient_Success() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Pagopa TPP");
        dto.setEntityId("12345678901");

        String savedTppId = "INTERNAL_ID_001";

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId, "12345678901", "Pagopa TPP")).thenReturn(Mono.just(savedTppId));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectNext(savedTppId)
                .verifyComplete();

        verify(tppConnector, times(1)).saveTpp(any(TppCreateRequest.class));
        verify(keycloakClientService, times(1)).createKeycloakClient(savedTppId, "12345678901", "Pagopa TPP");
        verify(tppConnector, never()).deleteTpp(anyString());
    }

    /**
     * Input DTO is NOT mutated: the mapper creates a new object; the original stays unchanged.
     */
    @Test
    void createTppAndKeycloakClient_DoesNotMutateInputDto() {
        TppDTOV1 original = new TppDTOV1();
        original.setBusinessName("Test TPP");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just("tpp-id"));
        when(keycloakClientService.createKeycloakClient(anyString(), any(), any())).thenReturn(Mono.just("tpp-id"));

        tppService.createTppAndKeycloakClient(original).block();

        assert original.getIdPsp() == null : "Original DTO must not be mutated";
        assert original.getLegalAddress() == null : "Original DTO must not be mutated";
    }

    /**
     * Connector failure: if TPP persistence fails, Keycloak must never be called.
     */
    @Test
    void createTppAndKeycloakClient_ErrorOnConnector_KeycloakNeverCalled() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Fail TPP");

        when(tppConnector.saveTpp(any(TppCreateRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Database error")));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectError(RuntimeException.class)
                .verify();

        verify(keycloakClientService, never()).createKeycloakClient(anyString(), any(), any());
        verify(tppConnector, never()).deleteTpp(anyString());
    }

    /**
     * Keycloak failure after DB save: compensation kicks in (deleteTpp is called),
     * then the original Keycloak exception is propagated.
     */
    @Test
    void createTppAndKeycloakClient_ErrorOnKeycloak_CompensationTriggered() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("KC Fail TPP");
        dto.setEntityId("12345678901");

        String savedTppId = "tpp-123";
        RuntimeException kcException = new RuntimeException("Keycloak unavailable");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId, "12345678901", "KC Fail TPP")).thenReturn(Mono.error(kcException));
        when(tppConnector.deleteTpp(savedTppId)).thenReturn(Mono.empty());

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectError(RuntimeException.class)
                .verify();

        verify(tppConnector, times(1)).saveTpp(any(TppCreateRequest.class));
        verify(keycloakClientService, times(1)).createKeycloakClient(savedTppId, "12345678901", "KC Fail TPP");
        // Compensation must have been attempted
        verify(tppConnector, times(1)).deleteTpp(savedTppId);
    }

    /**
     * Double failure: Keycloak fails AND the compensating delete also fails.
     * The original Keycloak exception must still be propagated (not the delete exception).
     */
    @Test
    void createTppAndKeycloakClient_ErrorOnKeycloak_CompensationAlsoFails_OriginalErrorPropagated() {
        TppDTOV1 dto = new TppDTOV1();
        dto.setBusinessName("Double Fail TPP");
        dto.setEntityId("12345678901");

        String savedTppId = "tpp-456";
        RuntimeException kcException = new RuntimeException("Keycloak unavailable");

        when(tppConnector.saveTpp(any(TppCreateRequest.class))).thenReturn(Mono.just(savedTppId));
        when(keycloakClientService.createKeycloakClient(savedTppId, "12345678901", "Double Fail TPP")).thenReturn(Mono.error(kcException));
        when(tppConnector.deleteTpp(savedTppId))
                .thenReturn(Mono.error(new RuntimeException("DB also down")));

        StepVerifier.create(tppService.createTppAndKeycloakClient(dto))
                .expectErrorMatches(ex -> ex.getMessage().equals("Keycloak unavailable"))
                .verify();

        verify(tppConnector, times(1)).deleteTpp(savedTppId);
    }

    // ── deleteTppAndKeycloakClient ─────────────────────────────────────────────

    /**
     * Happy path: il connector risolve il tppId dall'entityId, poi Keycloak viene cancellato
     * e infine il connector elimina il TPP. Nessun errore.
     */
    @Test
    void deleteTppAndKeycloakClient_Success() {
        String entityId = "12345678901";
        String tppId    = "tpp-to-delete";

        when(tppConnector.getTppByEntityId(entityId)).thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.deleteKeycloakClient(tppId)).thenReturn(Mono.empty());
        when(tppConnector.deleteTpp(tppId)).thenReturn(Mono.empty());

        StepVerifier.create(tppService.deleteTppAndKeycloakClient(entityId))
                .verifyComplete();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).deleteKeycloakClient(tppId);
        verify(tppConnector, times(1)).deleteTpp(tppId);
    }

    /**
     * TPP non trovata: il connector emette ResourceNotFoundException.
     * Né Keycloak né deleteTpp devono essere chiamati.
     */
    @Test
    void deleteTppAndKeycloakClient_TppNotFound_ErrorPropagated_NothingElseCalled() {
        String entityId = "99999999999";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        StepVerifier.create(tppService.deleteTppAndKeycloakClient(entityId))
                .expectError(ResourceNotFoundException.class)
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, never()).deleteKeycloakClient(anyString());
        verify(tppConnector, never()).deleteTpp(anyString());
    }

    /**
     * Il connector risolve il tppId ma la cancellazione Keycloak fallisce.
     * L'errore si propaga e il connector deleteTpp NON viene chiamato.
     */
    @Test
    void deleteTppAndKeycloakClient_KeycloakFails_ErrorPropagated_ConnectorNotCalled() {
        String entityId = "12345678901";
        String tppId    = "tpp-kc-fail";

        when(tppConnector.getTppByEntityId(entityId)).thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.deleteKeycloakClient(tppId))
                .thenReturn(Mono.error(new RuntimeException("Keycloak delete failed")));

        StepVerifier.create(tppService.deleteTppAndKeycloakClient(entityId))
                .expectError(RuntimeException.class)
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).deleteKeycloakClient(tppId);
        verify(tppConnector, never()).deleteTpp(anyString());
    }

    /**
     * Keycloak ha successo ma il connector deleteTpp fallisce: l'errore si propaga.
     */
    @Test
    void deleteTppAndKeycloakClient_ConnectorFails_ErrorPropagated() {
        String entityId = "12345678901";
        String tppId    = "tpp-connector-fail";

        when(tppConnector.getTppByEntityId(entityId)).thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.deleteKeycloakClient(tppId)).thenReturn(Mono.empty());
        when(tppConnector.deleteTpp(tppId))
                .thenReturn(Mono.error(new RuntimeException("TPP service unavailable")));

        StepVerifier.create(tppService.deleteTppAndKeycloakClient(entityId))
                .expectErrorMatches(ex -> ex.getMessage().equals("TPP service unavailable"))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).deleteKeycloakClient(tppId);
        verify(tppConnector, times(1)).deleteTpp(tppId);
    }

    // ── getTppPagopaCredentials ────────────────────────────────────────────────

    /**
     * Happy path: il connector risolve il tppId dall'entityId, poi Keycloak restituisce le credenziali.
     * Verifica che le due chiamate avvengano in sequenza e che clientId e clientSecret siano corretti.
     */
    @Test
    void getTppPagopaCredentials_Success_ResolvesEntityIdThenFetchesCredentials() {
        String entityId = "12345678901";
        String tppId    = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";
        String secret   = "xYz123AbC456DeF789GhI012JkL345Mn";
        TppPagopaCredentialsDTOV1 expected = new TppPagopaCredentialsDTOV1(tppId, secret);

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.just(expected));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .assertNext(result -> {
                    assert result.getClientId().equals(tppId);
                    assert result.getClientSecret().equals(secret);
                })
                .verifyComplete();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
    }

    /**
     * TPP non trovata: il connector emette {@link ResourceNotFoundException}.
     * Keycloak NON deve essere chiamato.
     */
    @Test
    void getTppPagopaCredentials_TppNotFound_PropagatesResourceNotFoundException_KeycloakNeverCalled() {
        String entityId = "99999999999";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(entityId))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, never()).getPagopaClientCredentials(anyString());
    }

    /**
     * Il connector risolve il tppId ma il client Keycloak non esiste:
     * {@link ResourceNotFoundException} deve propagarsi.
     */
    @Test
    void getTppPagopaCredentials_KeycloakClientNotFound_PropagatesResourceNotFoundException() {
        String entityId = "12345678901";
        String tppId    = "tpp-orphan";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.error(new ResourceNotFoundException("Keycloak client", tppId)));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(tppId))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
    }

    /**
     * Keycloak non raggiungibile: {@link ExternalServiceException} deve propagarsi.
     */
    @Test
    void getTppPagopaCredentials_KeycloakUnavailable_PropagatesExternalServiceException() {
        String entityId = "12345678901";
        String tppId    = "tpp-kc-down";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.error(new ExternalServiceException("KEYCLOAK", "fetchClientSecret", "timeout")));

        StepVerifier.create(tppService.getTppPagopaCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("KEYCLOAK"))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
    }

    /**
     * Verifica che il connector emd-tpp venga chiamato per la risoluzione del tppId
     * (a differenza del vecchio flusso dove il connector non era coinvolto).
     */
    @Test
    void getTppPagopaCredentials_AlwaysCallsConnectorToResolveTppId() {
        String entityId = "12345678901";
        String tppId    = "tpp-resolved";
        TppPagopaCredentialsDTOV1 credentials = new TppPagopaCredentialsDTOV1(tppId, "secret");

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(keycloakClientService.getPagopaClientCredentials(tppId))
                .thenReturn(Mono.just(credentials));

        tppService.getTppPagopaCredentials(entityId).block();

        // Il connector DEVE essere chiamato (non è più bypass-ato)
        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        // Keycloak viene chiamato con il tppId risolto, NON con l'entityId
        verify(keycloakClientService, times(1)).getPagopaClientCredentials(tppId);
        verify(keycloakClientService, never()).getPagopaClientCredentials(entityId);
    }

    // ── getTppCredentials ──────────────────────────────────────────────────────

    /**
     * Happy path: il connector risolve il tppId dall'entityId, poi recupera il token section
     * dal servizio emd-tpp e lo mappa in {@link TokenSectionDTOV1} correttamente.
     */
    @Test
    void getTppCredentials_Success_ResolvesEntityIdThenFetchesTokenSection() {
        String entityId    = "12345678901";
        String tppId       = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";
        TokenSection tokenSection = new TokenSection(
                "application/json",
                Map.of("scope", "openid"),
                Map.of("client_secret", "s3cr3t"));

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(tppConnector.getTppToken(tppId))
                .thenReturn(Mono.just(tokenSection));

        StepVerifier.create(tppService.getTppCredentials(entityId))
                .assertNext(dto -> {
                    assertThat(dto.getContentType()).isEqualTo("application/json");
                    assertThat(dto.getPathAdditionalProperties()).containsEntry("scope", "openid");
                    assertThat(dto.getBodyAdditionalProperties()).containsEntry("client_secret", "s3cr3t");
                })
                .verifyComplete();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(tppConnector, times(1)).getTppToken(tppId);
        // Keycloak non deve essere coinvolto in questo flusso
        verify(keycloakClientService, never()).getPagopaClientCredentials(anyString());
    }

    /**
     * TPP non trovata: il connector emette {@link ResourceNotFoundException}.
     * {@code getTppToken} NON deve essere chiamato.
     */
    @Test
    void getTppCredentials_TppNotFound_PropagatesResourceNotFoundException_TokenNeverFetched() {
        String entityId = "99999999999";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        StepVerifier.create(tppService.getTppCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(entityId))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(tppConnector, never()).getTppToken(anyString());
    }

    /**
     * Il connector risolve il tppId ma {@code getTppToken} emette un errore (es. 404 o 502):
     * l'errore deve propagarsi correttamente.
     */
    @Test
    void getTppCredentials_GetTokenFails_PropagatesExternalServiceException() {
        String entityId = "12345678901";
        String tppId    = "tpp-token-fail";

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(tppConnector.getTppToken(tppId))
                .thenReturn(Mono.error(new ExternalServiceException("TPP_SERVICE", "getTppToken", "upstream error")));

        StepVerifier.create(tppService.getTppCredentials(entityId))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("TPP_SERVICE"))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(tppConnector, times(1)).getTppToken(tppId);
    }

    /**
     * Keycloak NON deve essere mai chiamato nel flusso {@code getTppCredentials}
     * (le credenziali vengono lette da emd-tpp, non da Keycloak).
     */
    @Test
    void getTppCredentials_NeverInteractsWithKeycloak() {
        String entityId = "12345678901";
        String tppId    = "tpp-no-kc";
        TokenSection tokenSection = new TokenSection("application/json", null, null);

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(tppConnector.getTppToken(tppId))
                .thenReturn(Mono.just(tokenSection));

        tppService.getTppCredentials(entityId).block();

        verify(keycloakClientService, never()).getPagopaClientCredentials(anyString());
        verify(keycloakClientService, never()).createKeycloakClient(anyString(), any(), any());
        verify(keycloakClientService, never()).deleteKeycloakClient(anyString());
    }

    // ── updateTppCredentials ───────────────────────────────────────────────────

    /**
     * Happy path: il connector risolve il tppId dall'entityId, poi invia l'aggiornamento
     * al servizio emd-tpp. Keycloak non viene mai coinvolto.
     */
    @Test
    void updateTppCredentials_Success_ResolvesEntityIdThenUpdates() {
        String entityId = "12345678901";
        String tppId    = "47fc5f3c-78e6-43c7-8d0f-8627fb1e9eff-1773761623176";
        TokenSectionDTOV1 dto = new TokenSectionDTOV1(
                "application/json",
                Map.of("scope", "openid"),
                Map.of("client_secret", "nuovo-secret"));

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(tppConnector.updateTppToken(eq(tppId), any(TokenSection.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(tppService.updateTppCredentials(entityId, dto))
                .verifyComplete();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(tppConnector, times(1)).updateTppToken(eq(tppId), any(TokenSection.class));
        verify(keycloakClientService, never()).getPagopaClientCredentials(anyString());
    }

    /**
     * TPP non trovata: il connector emette {@link ResourceNotFoundException}.
     * {@code updateTppToken} NON deve essere chiamato.
     */
    @Test
    void updateTppCredentials_TppNotFound_PropagatesResourceNotFoundException_UpdateNeverCalled() {
        String entityId = "99999999999";
        TokenSectionDTOV1 dto = new TokenSectionDTOV1("application/json", null, null);

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.error(new ResourceNotFoundException("TPP", entityId)));

        StepVerifier.create(tppService.updateTppCredentials(entityId, dto))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains(entityId))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(tppConnector, never()).updateTppToken(anyString(), any());
    }

    /**
     * emd-tpp non raggiungibile durante l'update: {@link ExternalServiceException} propagata.
     */
    @Test
    void updateTppCredentials_UpdateFails_PropagatesExternalServiceException() {
        String entityId = "12345678901";
        String tppId    = "tpp-update-fail";
        TokenSectionDTOV1 dto = new TokenSectionDTOV1("application/json", null, null);

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(tppConnector.updateTppToken(eq(tppId), any(TokenSection.class)))
                .thenReturn(Mono.error(new ExternalServiceException("TPP_SERVICE", "updateTppToken", "upstream error")));

        StepVerifier.create(tppService.updateTppCredentials(entityId, dto))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("TPP_SERVICE"))
                .verify();

        verify(tppConnector, times(1)).getTppByEntityId(entityId);
        verify(tppConnector, times(1)).updateTppToken(eq(tppId), any(TokenSection.class));
    }

    /**
     * Verifica che i campi del DTO vengano mappati correttamente nel {@link TokenSection}
     * passato al connector: contentType, pathAdditionalProperties e bodyAdditionalProperties.
     */
    @Test
    void updateTppCredentials_MapsAllDtoFieldsToConnectorTokenSection() {
        String entityId = "12345678901";
        String tppId    = "tpp-mapping-check";
        Map<String, String> path = Map.of("tenantId", "123456");
        Map<String, String> body = Map.of("client_id", "new-id", "client_secret", "new-secret");
        TokenSectionDTOV1 dto = new TokenSectionDTOV1("application/x-www-form-urlencoded", path, body);

        when(tppConnector.getTppByEntityId(entityId))
                .thenReturn(Mono.just(new TppEntityIdResponse(tppId)));
        when(tppConnector.updateTppToken(eq(tppId), any(TokenSection.class)))
                .thenAnswer(inv -> {
                    TokenSection sent = inv.getArgument(1);
                    assertThat(sent.getContentType()).isEqualTo("application/x-www-form-urlencoded");
                    assertThat(sent.getPathAdditionalProperties()).containsEntry("tenantId", "123456");
                    assertThat(sent.getBodyAdditionalProperties()).containsEntry("client_id", "new-id");
                    assertThat(sent.getBodyAdditionalProperties()).containsEntry("client_secret", "new-secret");
                    return Mono.empty();
                });

        StepVerifier.create(tppService.updateTppCredentials(entityId, dto))
                .verifyComplete();
    }
}
