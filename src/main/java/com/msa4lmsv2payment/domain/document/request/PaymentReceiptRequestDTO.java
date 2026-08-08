package com.msa4lmsv2payment.domain.document.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PaymentReceiptRequestDTO(
        @Schema(description = "등록금 고지 ID(SUCCEEDED 결제 이력이 있어야 발급 가능)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId
) {
}
