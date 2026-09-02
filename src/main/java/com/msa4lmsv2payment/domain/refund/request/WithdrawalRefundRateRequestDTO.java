package com.msa4lmsv2payment.domain.refund.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record WithdrawalRefundRateRequestDTO(
        @Schema(description = "등록금 고지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @Schema(description = "Academic에서 승인된 자퇴 신청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "자퇴 신청 ID는 필수입니다.") Long withdrawalId
) {
}
