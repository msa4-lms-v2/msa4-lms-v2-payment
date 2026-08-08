package com.msa4lmsv2payment.domain.payment.response;

import java.math.BigDecimal;

public record CheckoutSessionResponseDTO(
        Long paymentId,
        String orderId,
        String orderName,
        BigDecimal amount
) {
}
