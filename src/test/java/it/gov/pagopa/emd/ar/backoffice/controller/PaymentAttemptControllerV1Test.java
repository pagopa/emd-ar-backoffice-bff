package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.handler.ControllerExceptionHandler;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.controller.PaymentAttemptControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchItemDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptDetailDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.PaymentAttemptNotFoundException;
import it.gov.pagopa.emd.ar.backoffice.service.payment.PaymentAttemptService;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentAttemptControllerV1Test {
	private static final String ROUTE = "/emd/backoffice/api/v1/payment-core/attempts/search";
	private static final String SINGLE_ROUTE = "/emd/backoffice/api/v1/payment-core/attempts/{tppId}/{iun}";
	private PaymentAttemptService service;
	private WebTestClient webTestClient;

	@BeforeEach
	void setUp() {
		service = mock(PaymentAttemptService.class);
		Utilities utilities = mock(Utilities.class);
		when(utilities.getTraceId()).thenReturn("TEST-TRACE-ID");
		webTestClient = WebTestClient.bindToController(new PaymentAttemptControllerImplV1(service))
				.controllerAdvice(new ControllerExceptionHandler(utilities)).build();
	}

	private PaymentAttemptSearchResponseDTOV1 pagedResponse() {
		return PaymentAttemptSearchResponseDTOV1.builder()
				.content(List.of(PaymentAttemptSearchItemDTOV1.builder().iun("IUN-123")
						.tppId("tpp-456").attemptsCount(2).build()))
				.pageSize(20).totalElements(135L).totalPages(7)
				.nextCursor("507f1f77bcf86cd799439011").hasNext(true).build();
	}

	private PaymentAttemptResponseDTOV1 singleResponse() {
		return PaymentAttemptResponseDTOV1.builder()
				.originId("IUN-123")
				.tppId("tpp-456")
				.attemptDetails(List.of(PaymentAttemptDetailDTOV1.builder()
						.noticeNumber("302000000000000000")
						.paymentAttemptDate("2026-10-13T10:15:30.000+00:00")
						.fiscalCode("RSSMRA80A01H501U")
						.amount("1000")
						.build()))
				.build();
	}

	@Test
	void getSinglePaymentAttemptReturnsUnwrappedObjectAndForwardsIdentifiers() {
		when(service.getPaymentAttempt("tpp-456", "IUN-123")).thenReturn(Mono.just(singleResponse()));
		webTestClient.get().uri(SINGLE_ROUTE, "tpp-456", "IUN-123").exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.originId").isEqualTo("IUN-123")
				.jsonPath("$.tppId").isEqualTo("tpp-456")
				.jsonPath("$.attemptDetails").isArray()
				.jsonPath("$.attemptDetails.length()").isEqualTo(1)
				.jsonPath("$.attemptDetails[0].noticeNumber").isEqualTo("302000000000000000")
				.jsonPath("$.attemptDetails[0].paymentAttemptDate").isEqualTo("2026-10-13T10:15:30.000+00:00")
				.jsonPath("$.attemptDetails[0].fiscalCode").isEqualTo("RSSMRA80A01H501U")
				.jsonPath("$.attemptDetails[0].amount").isEqualTo("1000");
		verify(service).getPaymentAttempt("tpp-456", "IUN-123");
	}

	@Test
	void getSinglePaymentAttemptNotFoundReturnsExistingErrorDtoWithCoreMeaning() {
		when(service.getPaymentAttempt("tpp-456", "IUN-missing"))
				.thenReturn(Mono.error(new PaymentAttemptNotFoundException()));
		webTestClient.get().uri(SINGLE_ROUTE, "tpp-456", "IUN-missing").exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.code").isEqualTo("PAYMENT_ATTEMPT_NOT_FOUND")
				.jsonPath("$.message").isEqualTo("Payment attempt not found.")
				.jsonPath("$.traceId").isEqualTo("TEST-TRACE-ID");
	}

	@Test
	void getSinglePaymentAttemptUnexpectedCoreErrorReturns502() {
		when(service.getPaymentAttempt("tpp-456", "IUN-123")).thenReturn(Mono.error(
				new ExternalServiceException("PAYMENT_CORE", "getPaymentAttempt", "core error")));
		webTestClient.get().uri(SINGLE_ROUTE, "tpp-456", "IUN-123").exchange()
				.expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
				.expectBody().jsonPath("$.code").isEqualTo("GENERIC_ERROR");
	}

	@Test
	void getSinglePaymentAttemptRejectsBlankTppId() {
		StepVerifier.create(new PaymentAttemptControllerImplV1(service).getPaymentAttempt("", "IUN-123"))
				.expectErrorMatches(error -> error instanceof ValidationException
						&& error.getMessage().equals("Both tppId and iun must be provided."))
				.verify();
		verifyNoInteractions(service);
	}

	@Test
	void getSinglePaymentAttemptRejectsBlankIun() {
		StepVerifier.create(new PaymentAttemptControllerImplV1(service).getPaymentAttempt("tpp-456", "  "))
				.expectError(ValidationException.class)
				.verify();
		verifyNoInteractions(service);
	}

	@Test
	void searchWithBothFiltersReturnsPageWithNextCursor() {
		when(service.searchPaymentAttempts("IUN-123", "tpp-456", null, 20)).thenReturn(Mono.just(pagedResponse()));
		webTestClient.get().uri(ROUTE + "?iun=IUN-123&tppId=tpp-456").exchange()
				.expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBody().jsonPath("$.content[0].iun").isEqualTo("IUN-123")
				.jsonPath("$.content[0].tppId").isEqualTo("tpp-456")
				.jsonPath("$.content[0].attemptsCount").isEqualTo(2)
				.jsonPath("$.pageSize").isEqualTo(20)
				.jsonPath("$.totalElements").isEqualTo(135)
				.jsonPath("$.totalPages").isEqualTo(7)
				.jsonPath("$.hasNext").isEqualTo(true)
				.jsonPath("$.nextCursor").isEqualTo("507f1f77bcf86cd799439011");
		verify(service).searchPaymentAttempts("IUN-123", "tpp-456", null, 20);
	}

	@Test
	void searchAcceptsOnlyIunOrOnlyTppId() {
		when(service.searchPaymentAttempts("IUN-123", null, null, 20)).thenReturn(Mono.just(pagedResponse()));
		when(service.searchPaymentAttempts(null, "tpp-456", null, 20)).thenReturn(Mono.just(pagedResponse()));
		webTestClient.get().uri(ROUTE + "?iun=IUN-123").exchange().expectStatus().isOk();
		webTestClient.get().uri(ROUTE + "?tppId=tpp-456").exchange().expectStatus().isOk();
		verify(service).searchPaymentAttempts("IUN-123", null, null, 20);
		verify(service).searchPaymentAttempts(null, "tpp-456", null, 20);
	}

	@Test
	void searchForwardsCursorAndLimit() {
		String cursor = "507f1f77bcf86cd799439011";
		when(service.searchPaymentAttempts("IUN-123", null, cursor, 35)).thenReturn(Mono.just(pagedResponse()));
		webTestClient.get().uri(ROUTE + "?iun=IUN-123&cursor=" + cursor + "&limit=35")
				.exchange().expectStatus().isOk();
		verify(service).searchPaymentAttempts("IUN-123", null, cursor, 35);
	}

	@Test
	void missingFiltersInvalidCursorAndOutOfRangeLimitReturn400() {
		webTestClient.get().uri(ROUTE).exchange().expectStatus().isBadRequest();
		webTestClient.get().uri(ROUTE + "?iun=IUN-123&cursor=invalid").exchange().expectStatus().isBadRequest();
		webTestClient.get().uri(ROUTE + "?tppId=tpp-456&limit=101").exchange().expectStatus().isBadRequest();
		verifyNoInteractions(service);
	}

	@Test
	void emptyResultReturns200() {
		when(service.searchPaymentAttempts("IUN-empty", null, null, 20)).thenReturn(Mono.just(
				PaymentAttemptSearchResponseDTOV1.builder().content(List.of()).pageSize(20)
						.totalElements(0L).totalPages(0).hasNext(false).build()));
		webTestClient.get().uri(ROUTE + "?iun=IUN-empty").exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.content").isEmpty().jsonPath("$.pageSize").isEqualTo(20)
				.jsonPath("$.totalElements").isEqualTo(0).jsonPath("$.totalPages").isEqualTo(0)
				.jsonPath("$.hasNext").isEqualTo(false);
	}

	@Test
	void downstreamErrorReturns502() {
		when(service.searchPaymentAttempts(any(), any(), any(), anyInt())).thenReturn(Mono.error(
				new ExternalServiceException("PAYMENT_CORE", "searchPaymentAttempts", "core error")));
		webTestClient.get().uri(ROUTE + "?iun=IUN-123").exchange().expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
	}
}
