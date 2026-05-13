package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppIdResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPagopaCredentialsDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppPatchDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TokenSectionDTOV1;
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
     * Checks whether a TPP with the given {@code entityId} (CF or P.IVA) already exists
     * and returns its full details.
     *
     * <p>Returns HTTP 200 with a {@code TppResponseDTOV1} payload if found, or HTTP 404 if no TPP
     * exists for that {@code entityId}.</p>
     *
     * @param entityId the fiscal code (11 digits) or VAT number (up to 16 alphanumeric chars)
     * @return {@code Mono<ResponseEntity<TppResponseDTOV1>>} with full TPP details, or 404
     */
    @GetMapping(value = "tpp/{entityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TppResponseDTOV1>> getTppByEntityId(
            @PathVariable("entityId") String entityId);

    /**
     * <strong>TEST ONLY — NOT exposed on APIM.</strong>
     *
     * <p>Permanently deletes the TPP identified by {@code entityId} (CF o P.IVA) from the
     * emd-tpp service and removes the associated Keycloak OIDC client.</p>
     *
     * @param entityId the fiscal code (CF) or VAT number (P.IVA) of the TPP to delete
     * @return {@code Mono<ResponseEntity<Void>>} with HTTP 204 No Content on success
     */
    @DeleteMapping(value = "tpp/{entityId}")
    Mono<ResponseEntity<Void>> deleteTpp(@PathVariable("entityId") String entityId);

    /**
     * Retrieves the PagoPA credentials (Keycloak OIDC client ID and secret) for the TPP
     * identified by {@code entityId} (CF o P.IVA).
     *
     * <p>The BFF first resolves the {@code tppId} from the emd-tpp service using the
     * {@code entityId}, then queries Keycloak for the client credentials using that
     * {@code tppId}. No intermediate storage or caching is performed.</p>
     *
     * @param entityId the fiscal code (CF) or VAT number (P.IVA) of the TPP
     * @return {@code Mono<ResponseEntity<TppPagopaCredentialsDTOV1>>} HTTP 200 with credentials,
     *         404 if no TPP or Keycloak client exists for that {@code entityId}
     */
    @GetMapping(value = "tpp/{entityId}/credentials/pagopa", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TppPagopaCredentialsDTOV1>> getTppPagopaCredentials(
            @PathVariable("entityId") String entityId);

    /**
     * Retrieves the token-section credentials stored in the database for the TPP
     * identified by {@code entityId} (CF o P.IVA).
     *
     * <p>The BFF resolves the {@code tppId} from the emd-tpp service using the
     * {@code entityId}, then fetches the token section via
     * {@code GET /emd/tpp/{tppId}/token}. No intermediate storage or caching is performed.</p>
     *
     * @param entityId the fiscal code (CF) or VAT number (P.IVA) of the TPP
     * @return {@code Mono<ResponseEntity<TokenSectionDTOV1>>} HTTP 200 with the token
     *         configuration, or 404 if no TPP exists for that {@code entityId}
     */
    @GetMapping(value = "tpp/{entityId}/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TokenSectionDTOV1>> getTppCredentials(
            @PathVariable("entityId") String entityId);

    /**
     * Updates the token-section credentials stored in the database for the TPP
     * identified by {@code entityId} (CF o P.IVA).
     *
     * <p>The APIM extracts the {@code entityId} from the JWT claim {@code orgFiscalCode}
     * and rewrites the URL to include it as a path variable before forwarding to this BFF
     * endpoint. The BFF resolves the corresponding {@code tppId} and delegates the update
     * to emd-tpp via {@code PUT /update/{tppId}/token}.</p>
     *
     * <p><strong>Privacy:</strong> the body may contain sensitive values (e.g.
     * {@code client_secret}). The payload must never appear in logs.</p>
     *
     * @param entityId       the fiscal code (CF) or VAT number (P.IVA) injected by APIM
     * @param tokenSectionDTO the new token-section data to persist
     * @return {@code Mono<ResponseEntity<TokenSectionDTOV1>>} HTTP 200 with the persisted token section,
     *         404 if no TPP is found, 502 if emd-tpp is unreachable
     */
    @PutMapping(value = "tpp/{entityId}/credentials",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TokenSectionDTOV1>> updateTppCredentials(
            @PathVariable("entityId") String entityId,
            @Valid @RequestBody TokenSectionDTOV1 tokenSectionDTO);

    /**
     * Partially updates the TPP identified by {@code entityId} (CF o P.IVA).
     *
     * <p>The APIM extracts the {@code entityId} from the JWT claim {@code orgFiscalCode}
     * and rewrites the URL to include it as a path variable before forwarding to this BFF
     * endpoint. The BFF resolves the corresponding {@code tppId} and delegates the patch
     * to emd-tpp via {@code PATCH /emd/tpp/{tppId}}.</p>
     *
     * <p>Only non-null fields in the request body are applied; all others retain their
     * current values in the database.</p>
     *
     * @param entityId the fiscal code (CF) or VAT number (P.IVA) injected by APIM
     * @param patchDTO the partial update payload
     * @return {@code Mono<ResponseEntity<TppResponseDTOV1>>} HTTP 200 with the full updated TPP,
     *         404 if no TPP is found, 502 if emd-tpp is unreachable
     */
    @PatchMapping(value = "tpp/{entityId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<TppResponseDTOV1>> patchTpp(
            @PathVariable("entityId") String entityId,
            @Valid @RequestBody TppPatchDTOV1 patchDTO);
}
