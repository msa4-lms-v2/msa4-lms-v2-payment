package com.msa4lmsv2payment.domain.payment.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PgPaymentRequestDTO(
        @NotBlank(message = "orderId는 필수입니다.") String orderId,
        @NotBlank(message = "paymentKey는 필수입니다.") String paymentKey,
        @NotNull(message = "결제 금액은 필수입니다.") BigDecimal amount
) {
}
