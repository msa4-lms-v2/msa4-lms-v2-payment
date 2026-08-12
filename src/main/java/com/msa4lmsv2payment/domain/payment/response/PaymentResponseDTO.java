package com.msa4lmsv2payment.domain.payment.response;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PaymentResponseDTO(
        @Schema(description = "결제 ID", example = "10") Long id,
        @Schema(description = "등록금 고지 ID", example = "1") Long tuitionBillId,
        @Schema(description = "결제 금액", example = "4200000") BigDecimal amount,
        @Schema(description = "결제 수단", allowableValues = {"CARD", "VIRTUAL_ACCOUNT", "TRANSFER"}, example = "CARD") PaymentMethod method,
        @Schema(description = "토스페이먼츠 paymentKey. confirm 성공 후 채워짐", example = "tgen_20260813_001", nullable = true) String pgTransactionId,
        @Schema(description = "결제 상태. SUCCEEDED는 종결 상태이며 FAILED로 역전하지 않음", allowableValues = {"REQUESTED", "SUCCEEDED", "FAILED", "CANCELLED"}, example = "SUCCEEDED") PaymentStatus status
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
