package com.msa4lmsv2payment.domain.refund.response;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record RefundResponseDTO(
        @Schema(description = "환불 ID") Long id,
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "환불 유형") RefundType refundType,
        @Schema(description = "환불 금액") BigDecimal amount,
        @Schema(description = "적용된 환불률") BigDecimal refundRate,
        @Schema(description = "환불 상태") RefundStatus status,
        @Schema(description = "재시도 횟수(MAX_RETRY_ATTEMPTS=3 도달 시 최종 실패)") Integer retryCount
) {
    public static RefundResponseDTO from(Refund refund) {
        return new RefundResponseDTO(
                refund.getId(),
                refund.getTuitionBillId(),
                refund.getRefundType(),
                refund.getAmount(),
                refund.getRefundRate(),
                refund.getStatus(),
                refund.getRetryCount()
        );
    }
}
