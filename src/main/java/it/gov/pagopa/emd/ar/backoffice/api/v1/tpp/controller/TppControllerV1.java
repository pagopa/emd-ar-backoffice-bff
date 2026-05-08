package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RequestMapping("/emd/backoffice/api/v1")
public interface TppControllerV1 {


    /**
     * Endpoint to save TPP information. Expects a valid TppDTOV1 payload in the request body.
     * It will contact the TPP service to save the provided TPP information. Then it will create a new client
     * in Keycloak with the TPP information. Finally it will return the tppId of the saved TPP as response.
     *
     * @return {@code Mono<ResponseEntity<TppIdResponseDTOV1>>} The tppId with status OK
     */
    @PostMapping(value = "tpp")
    Mono<ResponseEntity<TppIdResponseDTOV1>> saveTpp(@Valid @RequestBody TppDTOV1 tppDTO);

    /**
     * Checks whether a TPP with the given {@code entityId} (CF or P.IVA) already exists.
     *
     * <p>Returns HTTP 200 with a {@code tppId} payload if found, or HTTP 404 if no TPP
     * exists for that {@code entityId}.</p>
     *
     * @param entityId the fiscal code (11 digits) or VAT number (up to 16 alphanumeric chars)
     * @return {@code Mono<ResponseEntity<TppIdResponseDTOV1>>} with the tppId, or 404
     */
    @GetMapping(value = "tpp", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TppIdResponseDTOV1>> getTppByEntityId(
            @RequestParam("entityId") String entityId);
}
