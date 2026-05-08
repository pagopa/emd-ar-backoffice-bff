package it.gov.pagopa.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class CorsConfig implements WebFluxConfigurer {

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins(allowedOrigins.split(","))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        // Explicit header list required when allowCredentials=true (CORS spec).
        // Using wildcard "*" with credentials is rejected by browsers.
        .allowedHeaders("Content-Type", "Authorization", "X-Trace-Id", "X-Request-Id")
        .allowCredentials(true)
        .maxAge(3600);
  }
}

