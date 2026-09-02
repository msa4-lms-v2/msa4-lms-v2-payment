package com.msa4lmsv2payment.domain.tuitionbill.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionPaymentStatusResponseDTO(
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "고지 금액") BigDecimal billingAmount,
        @Schema(description = "납부 기한") LocalDate dueDate,
        @Schema(description = "납부 상태") TuitionBillStatus status
) {
    public static TuitionPaymentStatusResponseDTO from(TuitionBill tuitionBill) {
        return new TuitionPaymentStatusResponseDTO(
                tuitionBill.getId(),
                tuitionBill.getBillingAmount(),
                tuitionBill.getDueDate(),
                tuitionBill.getStatus()
        );
    }
}
