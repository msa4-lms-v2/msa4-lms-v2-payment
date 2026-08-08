package com.msa4lmsv2payment.domain.payment.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;

import java.math.BigDecimal;

public record PaymentSummaryResponseDTO(
        Long tuitionBillId,
        BigDecimal billingAmount,
        BigDecimal totalScholarshipAmount,
        BigDecimal totalPaidAmount,
        BigDecimal remainingAmount,
        TuitionBillStatus status
) {
}
