package com.msa4lmsv2payment.domain.scholarshipapplication.request;

import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ScholarshipApplicationCreateRequestDTO(
        @Schema(description = "등록금 고지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @Schema(description = "장학금 유형", allowableValues = {"MERIT", "NEED_BASED", "OTHER"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "장학금 유형은 필수입니다.") ScholarshipType type,
        @Schema(description = "신청 금액", example = "1000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "신청 금액은 필수입니다.") @DecimalMin(value = "0.01", message = "신청 금액은 0보다 커야 합니다.") BigDecimal requestedAmount,
        @Schema(description = "신청 사유", example = "가계 곤란으로 인한 장학금 신청", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "신청 사유는 필수입니다.") @Size(max = 500, message = "신청 사유는 500자를 넘을 수 없습니다.") String reason
) {
}
