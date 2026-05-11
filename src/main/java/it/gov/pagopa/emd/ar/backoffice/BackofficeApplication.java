package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webflux.autoconfigure.error.ErrorWebFluxAutoConfiguration;
import reactor.core.publisher.Hooks;

import it.gov.pagopa.common.utils.Constants;

import java.util.TimeZone;

@SpringBootApplication(exclude = {ErrorWebFluxAutoConfiguration.class}, scanBasePackages = "it.gov.pagopa")
public class BackofficeApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
		// Propagates Reactor context (trace ID, MDC) automatically across thread boundaries
		// (e.g. boundedElastic scheduler used for blocking JWT validation).
		Hooks.enableAutomaticContextPropagation();
		SpringApplication.run(BackofficeApplication.class, args);
	}

}
