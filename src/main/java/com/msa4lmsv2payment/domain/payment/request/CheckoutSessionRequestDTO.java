package com.msa4lmsv2payment.domain.payment.request;

import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CheckoutSessionRequestDTO(
        @Schema(description = "등록금 고지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @Schema(description = "결제 수단", allowableValues = {"CARD", "VIRTUAL_ACCOUNT", "TRANSFER"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "결제 수단은 필수입니다.") PaymentMethod method,
        @Schema(description = "분할납부 회차 항목 ID. 지정하면 전액이 아니라 이 회차 금액만 청구한다(미지정 시 기존과 동일하게 전액 청구).", example = "1")
        Long installmentPlanItemId
) {
}
