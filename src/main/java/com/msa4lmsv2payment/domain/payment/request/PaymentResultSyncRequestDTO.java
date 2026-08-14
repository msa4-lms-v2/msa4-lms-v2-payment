package com.msa4lmsv2payment.domain.payment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PaymentResultSyncRequestDTO(
        @Schema(description = "토스페이먼츠 결제위젯이 발급한 결제 키", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "paymentKey는 필수입니다.") String paymentKey
) {
}
