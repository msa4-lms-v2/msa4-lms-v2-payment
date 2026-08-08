package com.msa4lmsv2payment.domain.refund.controller;

import com.msa4lmsv2payment.domain.refund.request.RefundRetryRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.VirtualAccountRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.refund.service.RefundService;
import com.msa4lmsv2payment.global.idempotency.IdempotencyService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefundController {

    private static final String ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS = "/api/refunds/virtual-account-requests";
    private static final String ENDPOINT_RETRY = "/api/refunds/retry";

    private final RefundService refundService;
    private final IdempotencyService idempotencyService;

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/academic-status/withdrawal-refund-estimate")
    public GlobalRes<WithdrawalRefundEstimateResponseDTO> getWithdrawalRefundEstimate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalRes.success(refundService.estimateWithdrawalRefund(currentUser, tuitionBillId));
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PatchMapping("/api/academic-status/withdrawal-refund-rate")
    public GlobalRes<RefundResponseDTO> applyWithdrawalRefundRate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid WithdrawalRefundRateRequestDTO request
    ) {
        return GlobalRes.success(refundService.applyWithdrawalRefundRate(currentUser, request));
    }

    // API_SPEC.md 2.1절 - Idempotency-Key는 결제·신청류 API에 필수. 동일 키+동일 요청 재시도는 통과시키고
    // (하위 로직이 upsert 성격이라 재실행해도 결과가 같다), 다른 요청에 같은 키를 재사용하면 거부한다(M2번).
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS)
    public GlobalRes<RefundResponseDTO> requestVirtualAccountRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid VirtualAccountRefundRequestDTO request
    ) {
        idempotencyService.verifyAndReserve(idempotencyKey, currentUser.id(), ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS, request);
        RefundResponseDTO response = refundService.requestVirtualAccountRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey);
        return GlobalRes.success(response);
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping(ENDPOINT_RETRY)
    public GlobalRes<RefundResponseDTO> retryFailedRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid RefundRetryRequestDTO request
    ) {
        idempotencyService.verifyAndReserve(idempotencyKey, currentUser.id(), ENDPOINT_RETRY, request);
        RefundResponseDTO response = refundService.retryFailedRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey);
        return GlobalRes.success(response);
    }
}
