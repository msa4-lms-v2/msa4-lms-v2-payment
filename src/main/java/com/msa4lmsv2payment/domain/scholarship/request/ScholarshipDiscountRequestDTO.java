package com.msa4lmsv2payment.domain.scholarship.request;

import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ScholarshipDiscountRequestDTO(
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @NotNull(message = "장학금 유형은 필수입니다.") ScholarshipType type,
        @NotNull(message = "장학금 금액은 필수입니다.") @DecimalMin(value = "0.01", message = "장학금 금액은 0보다 커야 합니다.") BigDecimal amount,
        @Size(max = 255, message = "사유는 255자를 넘을 수 없습니다.") String reason
) {
}
