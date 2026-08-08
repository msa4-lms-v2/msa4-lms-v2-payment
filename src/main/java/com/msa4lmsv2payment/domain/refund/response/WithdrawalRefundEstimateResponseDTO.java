package com.msa4lmsv2payment.domain.refund.response;

import java.math.BigDecimal;

public record WithdrawalRefundEstimateResponseDTO(
        Long tuitionBillId,
        BigDecimal billingAmount,
        BigDecimal refundRate,
        BigDecimal estimatedRefundAmount
) {
}
