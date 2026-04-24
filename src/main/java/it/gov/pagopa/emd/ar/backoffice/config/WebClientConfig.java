package it.gov.pagopa.emd.ar.backoffice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import it.gov.pagopa.common.config.rest.HttpClientConfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import it.gov.pagopa.common.config.rest.QueryParamsPlusEncoderInterceptor;

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
    @ConfigurationProperties(prefix = "rest.defaults")
    public HttpClientConfig httpClientConfig() {
        return new HttpClientConfig();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder, HttpClientConfig config) {
        
        // 1. Configurazione del Pool di connessioni (Sostituisce HttpUtils)
        ConnectionProvider connectionProvider = ConnectionProvider.builder("keycloak-pool")
                .maxConnections(config.getConnectionPool().getSize())
                .maxIdleTime(Duration.ofMinutes(config.getConnectionPool().getTimeToLiveMinutes()))
                .build();
        
        // 2. Configurazione del client Netty con i timeout
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.getTimeout().getConnectMillis())
                .responseTimeout(Duration.ofMillis(config.getTimeout().getReadMillis()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(config.getTimeout().getReadMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(config.getTimeout().getReadMillis(), TimeUnit.MILLISECONDS)));


        // 3. Ritorna il WebClient usando il builder di Spring (importante per Tracing e Metrics)
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // AGGIUNGIAMO IL FILTRO QUI:
                .filter(new QueryParamsPlusEncoderInterceptor())
                .build();
    }
}
