package it.gov.pagopa.emd.ar.backoffice.controller.v1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@Slf4j
public class TppControllerImplV1 implements TppControllerV1 {

    @Override
    public Mono<ResponseEntity<Map<String, String>>> test() {
        log.info("[AR-BFF][TPP_HEALTH] Health check called");
        return Mono.just(ResponseEntity.ok(Map.of(
            "status", "OK",
            "service", "emd-ar-backoffice-bff"
        )));
    }
}
