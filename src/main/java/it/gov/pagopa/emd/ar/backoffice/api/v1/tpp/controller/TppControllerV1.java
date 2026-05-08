package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestBody;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import jakarta.validation.Valid;
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

    /**
     * Endpoint to save TPP information. Expects a valid TppDTOV1 payload in the request body.
     * It will contact the TPP service to save the provided TPP information. Then it will create a new client
     * in Keycloak with the TPP information. Finally it will return the tppId of the saved TPP as response.
     *
     * @return {@code Mono<ResponseEntity<TppIdResponseV1>>} The tppId with status OK
     */
    @PostMapping(value = "tpp")
    Mono<ResponseEntity<TppIdResponseDTOV1>> saveTpp(@Valid @RequestBody TppDTOV1 tppDTO);
}
