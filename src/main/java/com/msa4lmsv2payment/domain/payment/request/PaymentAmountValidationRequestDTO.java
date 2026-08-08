package com.msa4lmsv2payment.domain.payment.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAmountValidationRequestDTO(
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @NotNull(message = "검증할 금액은 필수입니다.") BigDecimal amount
) {
}
