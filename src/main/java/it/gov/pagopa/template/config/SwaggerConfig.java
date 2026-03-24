package it.gov.pagopa.template.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * The Class SwaggerConfig.
 */
@Configuration
@OpenAPIDefinition(
  info = @io.swagger.v3.oas.annotations.info.Info(
    title = "${spring.application.name}",
    version = "${spring.application.version}",
    description = "Api and Models"
  )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SwaggerConfig {
  static {
    io.swagger.v3.core.jackson.ModelResolver.enumsAsRef = true;
  }
}
