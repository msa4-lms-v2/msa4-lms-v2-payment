package com.msa4lmsv2payment.domain.payment.response;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PaymentResponseDTO(
        @Schema(description = "결제 ID") Long id,
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "결제 금액") BigDecimal amount,
        @Schema(description = "결제 수단") PaymentMethod method,
        @Schema(description = "토스페이먼츠 paymentKey(confirm 성공 후 채워짐)") String pgTransactionId,
        @Schema(description = "결제 상태") PaymentStatus status
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
