package com.msa4lmsv2payment.domain.payment.response;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponseDTO(
        Long id,
        Long tuitionBillId,
        BigDecimal amount,
        PaymentMethod method,
        String pgTransactionId,
        PaymentStatus status
) {
    public static PaymentResponseDTO from(Payment payment) {
        return new PaymentResponseDTO(
                payment.getId(),
                payment.getTuitionBillId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getPgTransactionId(),
                payment.getStatus()
        );
    }
}
