package com.msa4lmsv2payment.domain.scholarship.response;

import java.math.BigDecimal;

public record PaymentScholarshipAllocationResponseDTO(
        Long tuitionBillId,
        BigDecimal billingAmount,
        BigDecimal totalScholarshipAmount,
        BigDecimal actualPaymentAmount
) {
}
