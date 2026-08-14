package com.msa4lmsv2payment.domain.refund.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record WithdrawalRefundEstimateResponseDTO(
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "환불 기준액(성공 결제 합계 - 성공 환불 합계)") BigDecimal refundableBase,
        @Schema(description = "적용될 환불률", example = "0.8333") BigDecimal refundRate,
        @Schema(description = "예상 환불금(환불 기준액 × 환불률)") BigDecimal estimatedRefundAmount
) {
}
