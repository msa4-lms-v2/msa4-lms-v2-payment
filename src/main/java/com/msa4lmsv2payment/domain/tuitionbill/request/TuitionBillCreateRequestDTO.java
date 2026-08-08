package com.msa4lmsv2payment.domain.tuitionbill.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionBillCreateRequestDTO(
        @Schema(description = "Academic.students.id (학번)", example = "20260001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "학번은 필수입니다.") Long studentId,
        @Schema(description = "Academic.semesters.id", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "학기 ID는 필수입니다.") Long semesterId,
        @Schema(description = "고지 금액", example = "4200000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "고지 금액은 필수입니다.") @DecimalMin(value = "0.01", message = "고지 금액은 0보다 커야 합니다.") BigDecimal billingAmount,
        @Schema(description = "납부 기한", example = "2026-09-30", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "납부 기한은 필수입니다.") LocalDate dueDate
) {
}
