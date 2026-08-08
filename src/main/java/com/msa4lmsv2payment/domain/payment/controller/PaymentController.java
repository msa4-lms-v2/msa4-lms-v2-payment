package com.msa4lmsv2payment.domain.payment.controller;

import com.msa4lmsv2payment.domain.payment.request.CheckoutSessionRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentAmountValidationRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentResultSyncRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PgPaymentRequestDTO;
import com.msa4lmsv2payment.domain.payment.response.CheckoutSessionResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentAmountValidationResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.global.idempotency.IdempotencyService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private static final String ENDPOINT_PG_REQUESTS = "/api/payments/pg-requests";

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping("/api/payments/payment-amount-validation")
    public GlobalRes<PaymentAmountValidationResponseDTO> validateAmount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid PaymentAmountValidationRequestDTO request
    ) {
        return GlobalRes.success(paymentService.validateAmount(currentUser, request));
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payments/checkout-session")
    public GlobalRes<CheckoutSessionResponseDTO> createCheckoutSession(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid CheckoutSessionRequestDTO request
    ) {
        return GlobalRes.success(paymentService.createCheckoutSession(currentUser, request));
    }

    // API_SPEC.md 2.1절 - 결제 API는 Idempotency-Key 필수(비멱등 PG 승인 재시도 대비, code_convention.md B17번).
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping(ENDPOINT_PG_REQUESTS)
    public GlobalRes<PaymentResponseDTO> requestPgPayment(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PgPaymentRequestDTO request
    ) {
        idempotencyService.verifyAndReserve(idempotencyKey, currentUser.id(), ENDPOINT_PG_REQUESTS, request);
        PaymentResponseDTO response = paymentService.requestPgPayment(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey);
        return GlobalRes.success(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/payments/payment-results")
    public GlobalRes<PaymentResponseDTO> syncPaymentResult(
            @AuthenticationPrincipal CurrentUser admin,
            @RequestBody @Valid PaymentResultSyncRequestDTO request
    ) {
        return GlobalRes.success(paymentService.syncPaymentResult(admin, request));
    }
}
