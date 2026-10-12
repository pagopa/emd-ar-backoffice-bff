package it.gov.pagopa.emd.ar.backoffice.controller;

import it.gov.pagopa.common.utils.Utilities;
import it.gov.pagopa.emd.ar.backoffice.api.handler.ControllerExceptionHandler;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.controller.PaymentAttemptControllerImplV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchItemDTOV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.payment.dto.PaymentAttemptSearchResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.domain.exception.ExternalServiceException;
import it.gov.pagopa.emd.ar.backoffice.service.payment.PaymentAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentAttemptControllerV1Test {
	private static final String ROUTE = "/emd/backoffice/api/v1/payment-core/attempts/search";
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
				.payments(List.of(PaymentAttemptSearchItemDTOV1.builder().iun("IUN-123")
						.tppId("tpp-456").attemptsCount(2).build()))
				.returnedCount(1).attemptsCount(2L).nextCursor("507f1f77bcf86cd799439011")
				.hasMore(true).build();
	}

	@Test
	void searchWithBothFiltersReturnsPageWithNextCursor() {
		when(service.searchPaymentAttempts("IUN-123", "tpp-456", null, 20)).thenReturn(Mono.just(pagedResponse()));
		webTestClient.get().uri(ROUTE + "?iun=IUN-123&tppId=tpp-456").exchange()
				.expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectBody().jsonPath("$.payments[0].tppId").isEqualTo("tpp-456")
				.jsonPath("$.hasMore").isEqualTo(true)
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
				PaymentAttemptSearchResponseDTOV1.builder().payments(List.of()).returnedCount(0)
						.attemptsCount(0L).hasMore(false).build()));
		webTestClient.get().uri(ROUTE + "?iun=IUN-empty").exchange().expectStatus().isOk()
				.expectBody().jsonPath("$.payments").isEmpty().jsonPath("$.returnedCount").isEqualTo(0);
	}

	@Test
	void downstreamErrorReturns502() {
		when(service.searchPaymentAttempts(any(), any(), any(), anyInt())).thenReturn(Mono.error(
				new ExternalServiceException("PAYMENT_CORE", "searchPaymentAttempts", "core error")));
		webTestClient.get().uri(ROUTE + "?iun=IUN-123").exchange().expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
	}
}
