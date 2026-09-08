package it.gov.pagopa.emd.ar.backoffice.connector.message;

import java.time.LocalDateTime;
import java.util.List;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import reactor.core.publisher.Mono;

public interface MessageConnector {
    
    /**
     * Performs a paginated search for Messages by calling
     * {@code GET /emd/message-core/search} on the emd-message service.
     *
     * <p>Parameters are optional: pass {@code null} or empty string for those that
     * should be omitted from the query string. 
     * </p>
     *
     * <p>The optional {@code fields} parameter allows requesting only a subset of the
     * fields of each Message element in {@code content}. 
     * </p>
     *
     * <p>If an unknown field name is included in {@code fields}, the upstream returns
     * HTTP 400 ({@code INVALID_SEARCH_FIELD}), which is mapped to
     * {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException}.</p>
     *
     * @param messageId     optional exact-match filter on the message ID
     * @param recipientId  optional partial (case-insensitive) match on the recipientId name
     * @param originId     optional exact-match filter on the origin ID
     * @param startDate    optional start date for message registration period
     * @param endDate      optional end date for message registration period
     * @param page         zero-based page index (negative values are normalised to 0 upstream)
     * @param size         page size (values &lt;= 0 default to 10 upstream; values &gt; 100 are capped at 100)
     * @param fields       optional list of field names to include in each {@code content} element;
     *                     {@code null} or empty means use upstream defaults
     * @return {@code Mono<MessageSearchResponseDTOV1>} with the paginated result, or an
     *         {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException}
     *         for an invalid field name (upstream 400), or an
     *         {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException}
     *         on any other upstream error (401/429/500)
     */
    Mono<MessageSearchResponseDTOV1> searchMessages(String messageId, String recipientId, String originId, LocalDateTime startDate, LocalDateTime endDate, int page, int size, List<String> fields);

}
