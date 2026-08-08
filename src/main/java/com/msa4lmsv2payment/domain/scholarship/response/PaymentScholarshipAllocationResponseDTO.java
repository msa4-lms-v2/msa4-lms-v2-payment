package com.msa4lmsv2payment.domain.scholarship.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PaymentScholarshipAllocationResponseDTO(
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "고지 금액") BigDecimal billingAmount,
        @Schema(description = "적용된 장학금 합계") BigDecimal totalScholarshipAmount,
        @Schema(description = "실납부액(고지금액 - 장학금 합계)") BigDecimal actualPaymentAmount
) {
}
