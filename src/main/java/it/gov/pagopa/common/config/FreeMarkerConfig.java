package it.gov.pagopa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FreeMarkerConfig {

    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() {
        freemarker.template.Configuration cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setInterpolationSyntax(freemarker.template.Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        return cfg;
    }
}