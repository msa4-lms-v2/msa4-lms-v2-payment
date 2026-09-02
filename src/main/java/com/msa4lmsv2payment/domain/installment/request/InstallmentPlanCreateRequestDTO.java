package com.msa4lmsv2payment.domain.installment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InstallmentPlanCreateRequestDTO(
        @Schema(description = "등록금 고지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @Schema(description = "분할 회차 수(2~4회)", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "분할 회차 수는 필수입니다.")
        @Min(value = 2, message = "분할 회차는 최소 2회입니다.")
        @Max(value = 4, message = "분할 회차는 최대 4회입니다.") Integer totalRounds
) {
}
