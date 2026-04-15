package it.gov.pagopa.emd.ar.backoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Costruzione del bean ObjectMapper. Implementato per dare la possibilità di creare il mapper una sola volta
 * e iniettarlo dove necessario, evitando di creare nuove istanze ogni volta.
 */
@Configuration
public class JacksonConfig {
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Registra il supporto per le nuove date Java 8+ (LocalDate, etc.)
        mapper.registerModule(new JavaTimeModule());
        // Non fallire se il JSON ha campi che il DTO non ha
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Scrivi le date in formato ISO-8601 invece di timestamp numerici
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}
