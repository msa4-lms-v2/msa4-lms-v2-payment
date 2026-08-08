package com.msa4lmsv2payment.domain.refund.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record WithdrawalRefundEstimateResponseDTO(
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "고지 금액") BigDecimal billingAmount,
        @Schema(description = "적용될 환불률", example = "0.8333") BigDecimal refundRate,
        @Schema(description = "예상 환불금(고지금액 × 환불률)") BigDecimal estimatedRefundAmount
) {
}
