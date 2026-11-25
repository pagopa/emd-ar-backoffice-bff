package it.gov.pagopa.emd.ar.backoffice.api.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import it.gov.pagopa.common.utils.MemoryAppender;
import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerExceptionHandlerLoggingTest {

    private static final String FISCAL_CODE = "RSSMRA85T10A562S";
    // SHA-256 of FISCAL_CODE computed independently of the production code
    private static final String HASH = "bd807ad78163541b86ec74dec91f028a83299ba3adc7e1345f8c213573cd22be";

    private Logger handlerLogger;
    private MemoryAppender memoryAppender;

    @BeforeEach
    void setUp() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        memoryAppender = new MemoryAppender();
        memoryAppender.setContext(context);
        memoryAppender.start();
        handlerLogger = context.getLogger(ControllerExceptionHandler.class);
        handlerLogger.setLevel(Level.INFO);
        handlerLogger.addAppender(memoryAppender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(memoryAppender);
        memoryAppender.stop();
    }

    @Test
    void hashesFiscalCodeFromCitizenDeleteRequestDetails() {
        assertThat(ControllerExceptionHandler.getRequestDetails(request(HttpMethod.DELETE, "/emd/backoffice/api/v1/citizen/" + FISCAL_CODE)))
                .isEqualTo("DELETE /emd/backoffice/api/v1/citizen/" + HASH)
                .doesNotContain(FISCAL_CODE);
    }

    @Test
    void hashesFiscalCodeFromCitizenConsentPostRequestDetails() {
        assertThat(ControllerExceptionHandler.getRequestDetails(
                request(HttpMethod.POST, "/emd/backoffice/api/v1/citizen/" + FISCAL_CODE + "/consents/tpp1")))
                .isEqualTo("POST /emd/backoffice/api/v1/citizen/" + HASH + "/consents/tpp1")
                .doesNotContain(FISCAL_CODE);
    }

    @Test
    void failedConsentPostDoesNotLogFiscalCode() {
        ControllerExceptionHandler handler = new ControllerExceptionHandler(mock(Utilities.class));
        ServerHttpRequest request = request(HttpMethod.POST,
                "/emd/backoffice/api/v1/citizen/" + FISCAL_CODE + "/consents/tpp1");

        handler.handleExternalServiceException(
                new ExternalServiceException("CITIZEN_SERVICE", "saveCitizenConsent", "Upstream request failed"),
                request);

        assertThat(memoryAppender.getLoggedEvents()).isNotEmpty();
        assertThat(memoryAppender.getLoggedEvents())
                .allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(FISCAL_CODE));
        assertThat(memoryAppender.getLoggedEvents())
                .anySatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("POST /emd/backoffice/api/v1/citizen/" + HASH + "/consents/tpp1"));
    }

    private static ServerHttpRequest request(HttpMethod method, String pathValue) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath path = mock(RequestPath.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getPath()).thenReturn(path);
        when(path.value()).thenReturn(pathValue);
        return request;
    }
}
