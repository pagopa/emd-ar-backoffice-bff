package it.gov.pagopa.emd.ar.backoffice.service.message;

import java.time.LocalDateTime;
import java.util.List;

import it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto.MessageSearchResponseDTOV1;
import reactor.core.publisher.Mono;

public interface MessageCoreService {
    
    /**
     * Searches for Messages using a paginated, filtered query.
     *
     * <p>Delegates directly to the upstream {@code GET /emd/message-core/search} endpoint via the
     * connector. Filter parameters are optional — pass {@code null} or empty string to
     * omit them. 
     * </p>
     *
     * <p>The optional {@code fields} list restricts which fields are populated in each
     * element of {@code content}. When {@code null} or empty, upstream defaults apply.
     *  An invalid field name causes HTTP 400
     * ({@link it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException}).</p>
     *
     * @param messageId     optional exact-match filter on the entity fiscal/VAT code
     * @param recipientId  optional partial (case-insensitive) match on the recipientId name
     * @param originId     optional exact-match filter on the origin ID
     * @param startDate    optional start date for message registration period
     * @param endDate      optional end date for message registration period
     * @param page         zero-based page index (default 0)
     * @param size         page size (default 10, max 100)
     * @param fields       optional list of field names to include in each result element
     * @return {@code Mono<MessageSearchResponseDTOV1>} with the paginated result, or an
     *         {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.InvalidSearchFieldException}
     *         (HTTP 400) for an invalid field, or an
     *         {@link it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException}
     *         (HTTP 502) on any other upstream error
     */
    Mono<MessageSearchResponseDTOV1> searchMessages(String messageId, String recipientId, String originId, LocalDateTime startDate, LocalDateTime endDate, int page, int size, List<String> fields);
    
}
