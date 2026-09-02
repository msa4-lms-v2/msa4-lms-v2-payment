package com.msa4lmsv2payment.domain.payment.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PaymentSummaryResponseDTO(
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "고지 금액") BigDecimal billingAmount,
        @Schema(description = "적용된 장학금 합계") BigDecimal totalScholarshipAmount,
        @Schema(description = "SUCCEEDED 결제 누적 합계") BigDecimal totalPaidAmount,
        @Schema(description = "남은 납부액(실납부액 - 누적 납부액, 최소 0)") BigDecimal remainingAmount,
        @Schema(description = "납부 상태") TuitionBillStatus status
) {
}
