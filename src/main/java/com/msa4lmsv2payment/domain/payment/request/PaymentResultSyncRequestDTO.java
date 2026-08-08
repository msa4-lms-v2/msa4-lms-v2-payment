package com.msa4lmsv2payment.domain.payment.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentResultSyncRequestDTO(
        @NotBlank(message = "orderId는 필수입니다.") String orderId,
        @NotBlank(message = "paymentKey는 필수입니다.") String paymentKey
) {
}
