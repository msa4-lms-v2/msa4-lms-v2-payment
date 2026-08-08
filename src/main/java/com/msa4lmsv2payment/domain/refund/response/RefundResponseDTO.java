package com.msa4lmsv2payment.domain.refund.response;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;

import java.math.BigDecimal;

public record RefundResponseDTO(
        Long id,
        Long tuitionBillId,
        RefundType refundType,
        BigDecimal amount,
        BigDecimal refundRate,
        RefundStatus status,
        Integer retryCount
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
