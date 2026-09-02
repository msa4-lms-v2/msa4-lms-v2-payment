package com.msa4lmsv2payment.domain.payment.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PaymentAmountValidationResponseDTO(
        @Schema(description = "요청 금액이 서버 계산값과 일치하는지 여부") boolean valid,
        @Schema(description = "서버가 계산한 실납부액") BigDecimal expectedAmount
) {
}
