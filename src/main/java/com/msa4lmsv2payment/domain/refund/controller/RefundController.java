package com.msa4lmsv2payment.domain.refund.controller;

import com.msa4lmsv2payment.domain.refund.request.VirtualAccountRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.refund.service.RefundService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

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

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/refunds/virtual-account-requests")
    public GlobalRes<RefundResponseDTO> requestVirtualAccountRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid VirtualAccountRefundRequestDTO request
    ) {
        return GlobalRes.success(refundService.requestVirtualAccountRefund(currentUser, request));
    }
}
