package com.msa4lmsv2payment.domain.installment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InstallmentPlanReviewRequestDTO(
        @Schema(description = "심사 결정", allowableValues = {"APPROVE", "REJECT"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "심사 결정은 필수입니다.") InstallmentPlanDecision decision,
        @Schema(description = "반려 사유(REJECT일 때 필수)", example = "이미 등록된 분할납부 계획과 중복", maxLength = 255)
        @Size(max = 255, message = "반려 사유는 255자를 넘을 수 없습니다.") String rejectReason
) {
}
