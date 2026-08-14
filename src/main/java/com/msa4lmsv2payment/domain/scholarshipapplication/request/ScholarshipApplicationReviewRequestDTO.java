package com.msa4lmsv2payment.domain.scholarshipapplication.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScholarshipApplicationReviewRequestDTO(
        @Schema(description = "심사 결정", allowableValues = {"APPROVE", "REJECT"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "심사 결정은 필수입니다.") ScholarshipApplicationDecision decision,
        @Schema(description = "반려 사유(REJECT일 때 필수)", example = "제출 서류 미비", maxLength = 255)
        @Size(max = 255, message = "반려 사유는 255자를 넘을 수 없습니다.") String rejectReason
) {
}
