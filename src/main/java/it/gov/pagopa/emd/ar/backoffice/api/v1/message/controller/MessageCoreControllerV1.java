package it.gov.pagopa.emd.ar.backoffice.api.v1.message.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import reactor.core.publisher.Mono;

@RequestMapping ("/emd/backoffice/api/v1")
public interface MessageCoreControllerV1 {
    
    /**
     * Performs a paginated search for Messages.
     *
     * <p>At least one filter ({@code messageId} or {@code recipientId} or {@code originId}) or messages sent
     * between {@code startDate} and {@code endDate} should be provided
     * for meaningful results, though both are technically optional.</p>
     *
     * <ul>
     *   <li>{@code messageId} — exact match on the message ID.</li>
     *   <li>{@code recipientId} — exact match on the recipient ID.</li>
     *   <li>{@code originId} — exact match on the origin ID.</li>
     *   <li>{@code page} — zero-based page index (default {@code 0}).</li>
     *   <li>{@code size} — page size (default {@code 10}, upstream cap {@code 100}).</li>
     *   <li>{@code fields} — optional multi-value list of field names to include in each
     *       result element. When absent, upstream defaults are used.</li>
     * </ul>
     *
     * @param messageId    optional exact-match filter on the message ID
     * @param recipientId  optional exact-match filter on the recipient ID
     * @param originId     optional exact-match filter on the origin ID
     * @param startDate    optional start date for message registration period
     * @param endDate      optional end date for message registration period
     * @param page         zero-based page index (default 0)
     * @param size         page size (default 10)
     * @param fields       optional list of field names to project onto each result element
     * @return {@code Mono<ResponseEntity<MessageSearchResponseDTOV1>>} HTTP 200 with the
     *         paginated result, HTTP 400 for an invalid field name, or HTTP 502 if
     *         the upstream emd-message-core service is unavailable
     */
    @GetMapping(value = "message-core/search", produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<MessageSearchResponseDTOV1>> searchMessages(
        @RequestParam(name = "messageId", required = false) String messageId,
        @RequestParam(name = "recipientId", required = false) String recipientId,
        @RequestParam(name = "originId", required = false) String originId,
        @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)  LocalDateTime startDate,
        @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)  LocalDateTime endDate,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "10") int size,
        @RequestParam(name = "fields", required = false) List<String> fields);

}
