package com.msa4lmsv2payment.domain.payment.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record CheckoutSessionResponseDTO(
        @Schema(description = "생성된 payments 행 ID") Long paymentId,
        @Schema(description = "결제창에 넘길 주문 ID(\"PAY-{paymentId}\")", example = "PAY-1") String orderId,
        @Schema(description = "결제창에 표시할 주문명", example = "등록금 납부") String orderName,
        @Schema(description = "결제 금액") BigDecimal amount
) {
}
