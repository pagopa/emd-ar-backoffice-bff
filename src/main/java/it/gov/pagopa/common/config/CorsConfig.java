package it.gov.pagopa.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsConfig implements WebFluxConfigurer {

  private List<String> allowedOrigins;

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins(allowedOrigins.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        // Explicit header list required when allowCredentials=true (CORS spec).
        // Using wildcard "*" with credentials is rejected by browsers.
        .allowedHeaders("Content-Type", "Authorization", "X-Trace-Id", "X-Request-Id","Accept", "X-Requested-With")
        .allowCredentials(true)
        .maxAge(3600);
  }
}

