package com.msa4lmsv2payment.domain.payment.request;

import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CheckoutSessionRequestDTO(
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @NotNull(message = "결제 수단은 필수입니다.") PaymentMethod method
) {
}
