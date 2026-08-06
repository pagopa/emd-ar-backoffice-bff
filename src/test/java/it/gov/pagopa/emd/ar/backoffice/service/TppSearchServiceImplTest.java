package it.gov.pagopa.emd.ar.backoffice.service;

import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.TppConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppSearchResponse;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakClientService;
import it.gov.pagopa.emd.ar.backoffice.service.tpp.TppServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests per il metodo {@code searchTpp} di {@link TppServiceImpl}.
 *
 * <p>Il {@link TppConnector} viene mockato. Il mapper ({@code TppConnectorMapper})
 * non è mockato — è logica pura senza effetti collaterali.</p>
 *
 * <p>Scenari coperti:
 * <ol>
 *   <li>Ricerca per entityId — response correttamente mappata</li>
 *   <li>Ricerca per businessName — response correttamente mappata</li>
 *   <li>Risposta con lista vuota (pagina vuota)</li>
 *   <li>Errore upstream → {@link ExternalServiceException} propagata</li>
 *   <li>Paginazione — i metadati di pagina vengono trasportati nel DTO</li>
 * </ol>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TppSearchServiceImplTest {

    @Mock
    private TppConnector tppConnector;

    @Mock
    private KeycloakClientService keycloakClientService;

    private TppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TppServiceImpl(tppConnector, keycloakClientService);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TppEntityIdResponse aTpp(String tppId, String businessName, String entityId) {
        return TppEntityIdResponse.builder()
                .tppId(tppId)
                .entityId(entityId)
                .businessName(businessName)
                .state(true)
                .build();
    }

    private TppSearchResponse pageOf(List<TppEntityIdResponse> content, int page, int size, long total, int totalPages) {
        return TppSearchResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Ricerca per entityId — il service delega al connector e mappa correttamente.
     */
    @Test
    void searchTpp_ByEntityId_ReturnsMappedResponse() {
        TppSearchResponse connectorResponse = pageOf(
                List.of(aTpp("tpp-001", "Acme Srl", "04256050875")),
                0, 10, 1, 1);

        when(tppConnector.searchTpp(eq("04256050875"), isNull(), eq(0), eq(10)))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchTpp("04256050875", null, 0, 10))
                .assertNext(dto -> {
                    assertThat(dto.getTotalElements()).isEqualTo(1);
                    assertThat(dto.getTotalPages()).isEqualTo(1);
                    assertThat(dto.getPage()).isEqualTo(0);
                    assertThat(dto.getSize()).isEqualTo(10);
                    assertThat(dto.getContent()).hasSize(1);
                    assertThat(dto.getContent().get(0).getTppId()).isEqualTo("tpp-001");
                    assertThat(dto.getContent().get(0).getBusinessName()).isEqualTo("Acme Srl");
                    assertThat(dto.getContent().get(0).getEntityId()).isEqualTo("04256050875");
                })
                .verifyComplete();
    }

    /**
     * Ricerca per businessName — il service delega al connector e mappa correttamente.
     */
    @Test
    void searchTpp_ByBusinessName_ReturnsMappedResponse() {
        TppSearchResponse connectorResponse = pageOf(
                List.of(
                        aTpp("tpp-001", "MDC Finance Srl", "01234567890"),
                        aTpp("tpp-002", "MDC Payments S.p.A.", "09876543210")),
                0, 20, 2, 1);

        when(tppConnector.searchTpp(isNull(), eq("MDC"), eq(0), eq(20)))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchTpp(null, "MDC", 0, 20))
                .assertNext(dto -> {
                    assertThat(dto.getContent()).hasSize(2);
                    assertThat(dto.getContent().get(0).getBusinessName()).isEqualTo("MDC Finance Srl");
                    assertThat(dto.getContent().get(1).getBusinessName()).isEqualTo("MDC Payments S.p.A.");
                })
                .verifyComplete();
    }

    /**
     * Risposta con pagina vuota: la lista {@code content} è vuota e i contatori sono zero.
     */
    @Test
    void searchTpp_EmptyPage_ReturnsEmptyContent() {
        TppSearchResponse connectorResponse = pageOf(List.of(), 0, 10, 0, 0);

        when(tppConnector.searchTpp(eq("99999999999"), isNull(), eq(0), eq(10)))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchTpp("99999999999", null, 0, 10))
                .assertNext(dto -> {
                    assertThat(dto.getContent()).isEmpty();
                    assertThat(dto.getTotalElements()).isEqualTo(0);
                    assertThat(dto.getTotalPages()).isEqualTo(0);
                })
                .verifyComplete();
    }

    /**
     * Errore upstream → {@link ExternalServiceException} viene propagata senza trasformazioni.
     */
    @Test
    void searchTpp_UpstreamError_PropagatesExternalServiceException() {
        when(tppConnector.searchTpp(isNull(), eq("ACME"), eq(0), eq(10)))
                .thenReturn(Mono.error(new ExternalServiceException("TPP_SERVICE", "searchTpp", "500 error")));

        StepVerifier.create(service.searchTpp(null, "ACME", 0, 10))
                .expectErrorMatches(ex -> ex instanceof ExternalServiceException
                        && ex.getMessage().contains("searchTpp"))
                .verify();
    }

    /**
     * Paginazione: i metadati di pagina vengono traportati nel DTO senza alterazioni.
     */
    @Test
    void searchTpp_Pagination_PreservesPageMetadata() {
        List<TppEntityIdResponse> items = List.of(
                aTpp("tpp-010", "Rossi Srl", "01000000001"),
                aTpp("tpp-011", "Bianchi Srl", "01000000002"));

        TppSearchResponse connectorResponse = pageOf(items, 3, 2, 137, 69);

        when(tppConnector.searchTpp(isNull(), eq("Srl"), eq(3), eq(2)))
                .thenReturn(Mono.just(connectorResponse));

        StepVerifier.create(service.searchTpp(null, "Srl", 3, 2))
                .assertNext((TppSearchResponseDTOV1 dto) -> {
                    assertThat(dto.getPage()).isEqualTo(3);
                    assertThat(dto.getSize()).isEqualTo(2);
                    assertThat(dto.getTotalElements()).isEqualTo(137);
                    assertThat(dto.getTotalPages()).isEqualTo(69);
                })
                .verifyComplete();
    }
}

