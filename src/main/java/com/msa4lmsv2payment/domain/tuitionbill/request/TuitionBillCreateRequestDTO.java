package com.msa4lmsv2payment.domain.tuitionbill.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionBillCreateRequestDTO(
        @NotNull(message = "학번은 필수입니다.") Long studentId,
        @NotNull(message = "학기 ID는 필수입니다.") Long semesterId,
        @NotNull(message = "고지 금액은 필수입니다.") @DecimalMin(value = "0.01", message = "고지 금액은 0보다 커야 합니다.") BigDecimal billingAmount,
        @NotNull(message = "납부 기한은 필수입니다.") LocalDate dueDate
) {
}
