package it.gov.pagopa.emd.ar.backoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webflux.autoconfigure.error.ErrorWebFluxAutoConfiguration;

import it.gov.pagopa.common.utils.Constants;

import java.util.TimeZone;

@SpringBootApplication(exclude = {ErrorWebFluxAutoConfiguration.class})
public class BackofficeApplication {

	public static void main(String[] args) {
    TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
		SpringApplication.run(BackofficeApplication.class, args);
	}

}
