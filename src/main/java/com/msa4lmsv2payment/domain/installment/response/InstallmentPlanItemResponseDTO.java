package com.msa4lmsv2payment.domain.installment.response;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentPlanItemResponseDTO(
        @Schema(description = "회차 항목 ID") Long id,
        @Schema(description = "회차 번호(1부터 시작)") Integer roundNo,
        @Schema(description = "회차 금액") BigDecimal amount,
        @Schema(description = "회차 납부기한") LocalDate dueDate,
        @Schema(description = "이 회차를 결제한 payments.id, 미결제면 null") Long paymentId,
        @Schema(description = "회차 상태", allowableValues = {"SCHEDULED", "PAID"}) InstallmentItemStatus status
) {
    public static InstallmentPlanItemResponseDTO from(InstallmentPlanItem item) {
        return new InstallmentPlanItemResponseDTO(
                item.getId(), item.getRoundNo(), item.getAmount(), item.getDueDate(),
                item.getPaymentId(), item.getStatus());
    }
}
