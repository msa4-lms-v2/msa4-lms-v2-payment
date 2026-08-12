package com.msa4lmsv2payment.domain.refund.response;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record RefundResponseDTO(
        @Schema(description = "환불 ID", example = "5") Long id,
        @Schema(description = "등록금 고지 ID", example = "1") Long tuitionBillId,
        @Schema(description = "환불 유형", allowableValues = {"WITHDRAWAL", "PG_CANCEL", "EXCESS_DEPOSIT"}, example = "WITHDRAWAL") RefundType refundType,
        @Schema(description = "환불 금액", example = "3499860") BigDecimal amount,
        @Schema(description = "적용된 환불률", example = "0.8333") BigDecimal refundRate,
        @Schema(description = "환불 상태. SUCCEEDED에서는 금액과 환불률을 변경할 수 없음", allowableValues = {"REQUESTED", "SUCCEEDED", "FAILED", "RETRYING"}, example = "REQUESTED") RefundStatus status,
        @Schema(description = "재시도 횟수. 3회 도달 시 추가 재시도 거부", example = "0") Integer retryCount
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
