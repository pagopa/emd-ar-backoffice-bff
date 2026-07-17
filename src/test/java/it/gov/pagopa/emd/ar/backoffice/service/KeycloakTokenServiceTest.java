package it.gov.pagopa.emd.ar.backoffice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import it.gov.pagopa.emd.ar.backoffice.service.auth.keycloak.KeycloakTokenServiceImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
  * Unit tests for {@link KeycloakTokenServiceImpl}.
  *
  * <p>This test class verifies the behavior of the portal token retrieval flow,
  * including:
  * <ul>
  *     <li>successful token retrieval from Keycloak;</li>
  *     <li>propagation of errors returned by the WebClient call;</li>
  *     <li>correct creation of the OAuth2 authorization code form payload.</li>
  * </ul>
  *
  * <p>The {@link WebClient} interaction is mocked in order to test only the
  * service behavior without performing real HTTP calls.
  */
@ExtendWith(MockitoExtension.class)
public class KeycloakTokenServiceTest {
    @InjectMocks
    private KeycloakTokenServiceImpl service;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    /**
     * Configures the mocked WebClient chain and injects the required service
     * configuration properties.
     *
     * <p>The configuration values are injected using {@link ReflectionTestUtils}
     * because they normally come from Spring application properties.
     */
    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(service, "backofficeAdminClientId", "client-id");
        ReflectionTestUtils.setField(service, "backofficeAdminClientSecret", "client-secret");
        ReflectionTestUtils.setField(service, "authServerUrl", "http://localhost/token");
        ReflectionTestUtils.setField(service, "realm", "test-realm");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(URI.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)).thenReturn(requestBodySpec);
        when(requestBodySpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

    }

    /**
     * Verifies that the service correctly returns the portal token when the
     * Keycloak token endpoint responds successfully.
     *
     * <p>The test validates that the response body is correctly propagated as
     * a {@code Map<String, Object>} through the reactive pipeline.
     */
    @Test
    void shouldReturnPortalToken() {

        Map<String, Object> expected = Map.of(
                "access_token", "jwt-token",
                "expires_in", 300
        );

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(expected));

        StepVerifier.create(service.getPortalToken("code", "verifier"))
                .assertNext(result -> {
                    assertThat(result)
                            .containsEntry("access_token", "jwt-token")
                            .containsEntry("expires_in", 300);
                })
                .verifyComplete();
    }

    /**
     * Verifies that errors returned during the WebClient execution are correctly
     * propagated to the reactive stream.
     *
     * <p>This scenario simulates a failure while invoking Keycloak and verifies
     * that the original exception is emitted by the returned {@link Mono}.
     */
    @Test
    void shouldPropagateError() {

        RuntimeException exception = new RuntimeException("Keycloak unavailable");

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(exception));

        StepVerifier.create(service.getPortalToken("code", "verifier"))
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(RuntimeException.class)
                            .hasMessage("Keycloak unavailable");
                })
                .verify();
    }

    /**
     * Verifies that the OAuth2 authorization code request payload sent to
     * Keycloak contains all required form parameters.
     *
     * <p>The test captures the request body passed to the WebClient and checks
     * the presence and correctness of:
     * <ul>
     *     <li>grant type;</li>
     *     <li>client credentials;</li>
     *     <li>authorization code;</li>
     *     <li>PKCE code verifier;</li>
     *     <li>redirect URI.</li>
     * </ul>
     */
    @Test
    void shouldBuildCorrectFormData() {

        ArgumentCaptor<MultiValueMap<String, String>> captor =
                ArgumentCaptor.forClass(MultiValueMap.class);

        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Collections.emptyMap()));

        service.getPortalToken("my-code", "my-verifier").block();

        verify(requestBodySpec).bodyValue(captor.capture());

        MultiValueMap<String, String> form = captor.getValue();

        assertThat(form.getFirst("grant_type")).isEqualTo("authorization_code");
        assertThat(form.getFirst("client_id")).isEqualTo("client-id");
        assertThat(form.getFirst("client_secret")).isEqualTo("client-secret");
        assertThat(form.getFirst("code")).isEqualTo("my-code");
        assertThat(form.getFirst("code_verifier")).isEqualTo("my-verifier");
        assertThat(form.getFirst("redirect_uri"))
                .isEqualTo("https://oauth.pstmn.io/v1/callback");
    }

}
