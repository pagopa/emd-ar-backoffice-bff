package it.gov.pagopa.emd.ar.backoffice.service.citizen;

import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenSearchItemDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.citizen.dto.CitizenSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.CitizenConnector;
import it.gov.pagopa.emd.ar.backoffice.connector.citizen.dto.CitizenSearchResponse;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Validates search inputs and delegates the request to the Citizen connector. */
@Slf4j
@Service
public class CitizenServiceImpl implements CitizenService {

    private static final Pattern ASCII_ALPHANUMERIC = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern COMPLETE_FISCAL_CODE = Pattern.compile(
            "[A-Za-z]{6}[0-9]{2}[A-Za-z][0-9]{2}[A-Za-z][0-9]{3}[A-Za-z]");
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final CitizenConnector citizenConnector;

    public CitizenServiceImpl(CitizenConnector citizenConnector) {
        this.citizenConnector = citizenConnector;
    }

    @Override
    public Mono<CitizenSearchResponseDTOV1> searchByFiscalCode(String fiscalCode, String cursor, String size) {
        final SearchParameters parameters;
        try {
            parameters = validateAndNormalize(fiscalCode, cursor, size);
        } catch (ValidationException exception) {
            return Mono.error(exception);
        }

        log.info("[AR-BFF][CITIZEN_SEARCH] Search started (cursorPresent={}, size={})",
                parameters.cursor() != null, parameters.size());
        return citizenConnector.searchByFiscalCode(parameters.fiscalCode(), parameters.cursor(), parameters.size())
                .map(CitizenServiceImpl::toApiResponse)
                .doOnSuccess(response -> log.info("[AR-BFF][CITIZEN_SEARCH] Search completed (totalElements={}, hasNext={})",
                        response.getTotalElements(), response.isHasNext()))
                .doOnError(error -> log.error("[AR-BFF][CITIZEN_SEARCH] Search failed: {}",
                        error.getClass().getSimpleName()));
    }

    private static SearchParameters validateAndNormalize(String fiscalCode, String cursor, String size) {
        if (fiscalCode == null || fiscalCode.length() < 3 || fiscalCode.length() > 16
                || !ASCII_ALPHANUMERIC.matcher(fiscalCode).matches()) {
            throw new ValidationException("fiscalCode must contain 3 to 16 ASCII alphanumeric characters.");
        }
        if (fiscalCode.length() == 16 && !COMPLETE_FISCAL_CODE.matcher(fiscalCode).matches()) {
            throw new ValidationException("A 16-character fiscalCode must match the complete fiscal-code format.");
        }

        String normalizedFiscalCode = fiscalCode.toUpperCase(Locale.ROOT);
        String normalizedCursor = null;
        if (cursor != null) {
            if (cursor.length() != 16 || !ASCII_ALPHANUMERIC.matcher(cursor).matches()) {
                throw new ValidationException("cursor must contain exactly 16 ASCII alphanumeric characters.");
            }
            normalizedCursor = cursor.toUpperCase(Locale.ROOT);
            if (!normalizedCursor.startsWith(normalizedFiscalCode)) {
                throw new ValidationException("cursor must start with the searched fiscalCode.");
            }
        }

        int parsedSize = DEFAULT_SIZE;
        if (size != null) {
            try {
                parsedSize = Integer.parseInt(size);
            } catch (NumberFormatException exception) {
                throw new ValidationException("size must be an integer between 1 and 100.");
            }
            if (parsedSize < 1 || parsedSize > MAX_SIZE) {
                throw new ValidationException("size must be an integer between 1 and 100.");
            }
        }
        return new SearchParameters(normalizedFiscalCode, normalizedCursor, parsedSize);
    }

    private static CitizenSearchResponseDTOV1 toApiResponse(CitizenSearchResponse response) {
        List<CitizenSearchItemDTOV1> content = response.getContent() == null ? null : response.getContent().stream()
                .map(item -> new CitizenSearchItemDTOV1(item.getFiscalCode(), item.getConsentCount()))
                .toList();
        return CitizenSearchResponseDTOV1.builder()
                .content(content)
                .pageSize(response.getPageSize())
                .totalElements(response.getTotalElements())
                .totalPages(response.getTotalPages())
                .nextCursor(response.getNextCursor())
                .hasNext(response.isHasNext())
                .build();
    }

    private record SearchParameters(String fiscalCode, String cursor, int size) { }
}

