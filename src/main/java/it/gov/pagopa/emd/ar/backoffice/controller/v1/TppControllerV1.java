package it.gov.pagopa.emd.ar.backoffice.controller.v1;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import reactor.core.publisher.Mono;

import java.util.Map;

@RequestMapping("/emd/backoffice/api/v1")
public interface TppControllerV1 {

    /**
     * Simple static endpoint to verify APIM routing and permissions.
     * No authentication required — returns a static OK payload.
     *
     * @return {@code Mono<ResponseEntity<Map>>} with status OK
     */
    @GetMapping(value = "tpp/test", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<Map<String, String>>> test();
}
