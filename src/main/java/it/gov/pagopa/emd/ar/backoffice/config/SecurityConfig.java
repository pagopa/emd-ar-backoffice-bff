package it.gov.pagopa.emd.ar.backoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disabilita CSRF per API REST
            .authorizeExchange(exchanges -> exchanges

                // Lascia tutte le API pubbliche
                .anyExchange().permitAll()
            )
            .build();
    }

}
