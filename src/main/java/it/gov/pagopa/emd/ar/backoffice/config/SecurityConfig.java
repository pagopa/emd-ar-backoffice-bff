package it.gov.pagopa.emd.ar.backoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disabilita CSRF per API REST
            .authorizeExchange(exchanges -> exchanges
                // Specifica l'API che richiede autenticazione
                //.pathMatchers("/emd/backoffice/api/auth/pagopa").authenticated()
                
                // Lascia tutte le altre API pubbliche
                .anyExchange().permitAll()
            )
        
            // OAuth2 Resource Server in modalità reattiva
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults()));
        
        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // Sostituisci con l'URL reale del set di chiavi pubbliche del fornitore del token
        return NimbusReactiveJwtDecoder.withJwkSetUri("https://dev.selfcare.pagopa.it/.well-known/jwks.json").build();
    }
}
