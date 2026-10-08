package it.gov.pagopa.emd.ar.backoffice.api.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerExceptionHandlerLoggingTest {

    @Test
    void redactsFiscalCodeFromCitizenDeleteRequestDetails() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath path = mock(RequestPath.class);
        when(request.getMethod()).thenReturn(HttpMethod.DELETE);
        when(request.getPath()).thenReturn(path);
        when(path.value()).thenReturn("/emd/backoffice/api/v1/citizen/RSSMRA85T10A562S");

        assertThat(ControllerExceptionHandler.getRequestDetails(request))
                .isEqualTo("DELETE /emd/backoffice/api/v1/citizen/[REDACTED]")
                .doesNotContain("RSSMRA85T10A562S");
    }
}
