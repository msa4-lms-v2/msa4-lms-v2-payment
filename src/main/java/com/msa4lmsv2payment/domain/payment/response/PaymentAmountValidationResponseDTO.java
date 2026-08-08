package com.msa4lmsv2payment.domain.payment.response;

import java.math.BigDecimal;

public record PaymentAmountValidationResponseDTO(
        boolean valid,
        BigDecimal expectedAmount
) {
}
