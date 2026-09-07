package com.msa4lmsv2payment.domain.refund.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PgCancelRefundRequestDTO(
        @Schema(description = "취소할 카드 결제 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "결제 ID는 필수입니다.") Long paymentId,
        @Schema(description = "취소 금액. 비우면 이 결제의 남은 전액을 취소한다(전체취소), 지정하면 그만큼만 취소한다(부분취소).",
                example = "100000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive(message = "취소 금액은 0보다 커야 합니다.") BigDecimal amount,
        @Schema(description = "취소 사유", example = "중복 결제", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "취소 사유는 필수입니다.") String reason
) {
}
