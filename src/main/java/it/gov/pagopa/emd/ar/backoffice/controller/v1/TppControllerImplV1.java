package it.gov.pagopa.emd.ar.backoffice.controller.v1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.service.TppServiceImpl;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@Slf4j
public class TppControllerImplV1 implements TppControllerV1 {

    private TppServiceImpl tppService;

    public TppControllerImplV1(TppServiceImpl tppService) {
        this.tppService = tppService;
    }

    @Override
    public Mono<ResponseEntity<Map<String, String>>> test() {
        log.info("[AR-BFF][TPP_HEALTH] Health check called");
        return Mono.just(ResponseEntity.ok(Map.of(
            "status", "OK",
            "service", "emd-ar-backoffice-bff"
        )));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<ResponseEntity<String>> saveTpp(TppDTOV1 tppDTO) {
        log.info("[AR-BFF][TPP_SAVE] Saving TPP");
        return tppService.createTppAndKeycloakClient(tppDTO)
                .map(ResponseEntity::ok);
    }
}
