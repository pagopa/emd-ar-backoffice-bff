package it.gov.pagopa.emd.ar.backoffice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Utilizzato per la chiamata verso Keycloak.
 * Configurazione del WebClient con timeout per le chiamate HTTP. Utilizza Netty come client sottostante e imposta timeout di connessione, lettura e scrittura a 5 secondi.
 * Questo aiuta a prevenire che le chiamate HTTP si blocchino indefinitamente in caso di problemi di rete o server non responsivi.
 */
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient webClient() {

        WebClient.Builder builder = WebClient.builder();

        // Configuriamo il client sottostante (Netty) per i timeout
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // Timeout connessione (5 sec)
                .responseTimeout(Duration.ofSeconds(5))            // Timeout risposta totale
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
