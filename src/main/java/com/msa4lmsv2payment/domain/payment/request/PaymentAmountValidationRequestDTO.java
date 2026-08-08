package com.msa4lmsv2payment.domain.payment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAmountValidationRequestDTO(
        @Schema(description = "등록금 고지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @Schema(description = "클라이언트가 결제하려는 금액(서버 계산값과 비교)", example = "4200000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "검증할 금액은 필수입니다.") BigDecimal amount
) {
}
