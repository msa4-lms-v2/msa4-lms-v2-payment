package com.msa4lmsv2payment.domain.payment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PgPaymentRequestDTO(
        @Schema(description = "결제창 연동에서 발급받은 주문 ID", example = "PAY-1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "orderId는 필수입니다.") String orderId,
        @Schema(description = "토스페이먼츠 결제위젯이 발급한 결제 키", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "paymentKey는 필수입니다.") String paymentKey,
        @Schema(description = "결제 금액(서버 저장값과 다르면 400)", example = "4200000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "결제 금액은 필수입니다.") BigDecimal amount
) {
}
