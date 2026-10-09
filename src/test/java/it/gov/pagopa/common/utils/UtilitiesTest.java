package it.gov.pagopa.common.utils;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.util.Base64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilitiesTest {

    @Mock
    private Tracer tracerMock;

    @Mock
    private Span spanMock;

    @Mock
    private TraceContext traceContextMock;

    private Utilities utilities;

    @BeforeEach
    void setUp() {
        utilities = new Utilities(tracerMock);
    }

    @Test
    void testGetTraceId() {
        // Given
        String expectedResult = "TRACEID";
        
        // Simuliamo la catena: tracer.currentSpan().context().traceId()
        Mockito.when(tracerMock.currentSpan()).thenReturn(spanMock);
        Mockito.when(spanMock.context()).thenReturn(traceContextMock);
        Mockito.when(traceContextMock.traceId()).thenReturn(expectedResult);

        // When
        String result = utilities.getTraceId();

        // Then
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    void testGetTraceIdWhenNoSpan() {
        // Given: non c'è alcuno span attivo
        Mockito.when(tracerMock.currentSpan()).thenReturn(null);

        // When
        String result = utilities.getTraceId();

        // Then
        Assertions.assertEquals("no-trace-id", result);
    }

    // Questi metodi ora servono solo per i test degli altri componenti che usano MDC (se ancora presenti)
    // Ma per UtilitiesTest sono obsoleti. Li teniamo solo se servono a TraceIdObservationFilterTest
    public static void setTraceId(String traceId) {
        // In WebFlux questo metodo è deprecato, ma lo lasciamo per compatibilità con i tuoi vecchi test
        org.slf4j.MDC.put("traceId", traceId);
    }

    public static void clearTraceIdContext(){
        org.slf4j.MDC.clear();
    }

    /**
     * Happy path test: verifica che getEmailFromToken ritorni l'email corretta quando il token è valido e contiene il campo email.
     */
    @Test
    void testGetEmailFromToken_Success() {
        // Given: un payload JWT valido con il campo email
        String email = "test@example.com";
        String payload = "{\"email\":\"" + email + "\",\"sub\":\"12345\"}";
        String encodedPayload = Base64.getUrlEncoder().encodeToString(payload.getBytes());
        String authHeader = "Bearer header." + encodedPayload + ".signature";

        // When
        String result = Utilities.getEmailFromToken(authHeader);

        // Then
        Assertions.assertEquals(email, result);
    }

    /**
     * Test di fallimento: verifica che getEmailFromToken ritorni "unknown-user" quando il token è valido ma non contiene il campo email.
     */
    @Test
    void testGetEmailFromToken_NoEmailInPayload() {
        // Given: payload valido ma senza campo "email"
        String payload = "{\"sub\":\"12345\", \"name\":\"John\"}";
        String encodedPayload = Base64.getUrlEncoder().encodeToString(payload.getBytes());
        String authHeader = "Bearer header." + encodedPayload + ".signature";

        // When
        String result = Utilities.getEmailFromToken(authHeader);

        // Then
        Assertions.assertEquals("unknown-user", result);
    }

    /**
     * Test di fallimento: verifica che getEmailFromToken ritorni un messaggio di errore quando l'header Authorization è nullo.
     */
    @Test
    void testGetEmailFromToken_NullHeader() {
        // When
        String result = Utilities.getEmailFromToken(null);

        // Then
        Assertions.assertTrue(result.contains("no Authorization header"));
    }

    /**
     * Test di fallimento: verifica che getEmailFromToken ritorni un messaggio di errore quando il token non è un Bearer token.
     */
    @Test
    void testGetEmailFromToken_NotBearer() {
        // When
        String result = Utilities.getEmailFromToken("Basic dXNlcjpwYXNz");

        // Then
        Assertions.assertTrue(result.contains("not Bearer token"));
    }

    /**
     * Test di fallimento: verifica che getEmailFromToken ritorni "invalid-token" quando il token non ha almeno 2 parti separate da punto.
     */
    @Test
    void testGetEmailFromToken_InvalidFormat() {
        // Given: un token che non ha almeno 2 parti separate da punto
        String authHeader = "Bearer invalidTokenFormat";

        // When
        String result = Utilities.getEmailFromToken(authHeader);

        // Then
        Assertions.assertEquals("invalid-token", result);
    }

    /**
     * Test di fallimento: verifica che getEmailFromToken ritorni "error-parsing-token" quando la parte centrale del token non è Base64 valida.
     */
    @Test
    void testGetEmailFromToken_MalformedBase64() {
        // Given: una parte centrale che non è Base64 valida
        String authHeader = "Bearer header.!!!NotBase64!!!.signature";

        // When
        String result = Utilities.getEmailFromToken(authHeader);

        // Then
        Assertions.assertEquals("error-parsing-token", result);
    }

    /**
     * Test di fallimento: verifica che getEmailFromToken ritorni "error-parsing-token" quando la parte centrale del token è Base64 valida ma non rappresenta un JSON valido.
     */
    @Test
    void testGetEmailFromToken_InvalidJsonPayload() {
        // Given: Base64 valido ma il contenuto non è un JSON
        String payload = "not-a-json";
        String encodedPayload = Base64.getUrlEncoder().encodeToString(payload.getBytes());
        String authHeader = "Bearer header." + encodedPayload + ".signature";

        // When
        String result = Utilities.getEmailFromToken(authHeader);

        // Then
        Assertions.assertEquals("error-parsing-token", result);
    }

}