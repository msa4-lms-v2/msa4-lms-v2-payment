package com.msa4lmsv2payment.domain.scholarshipapplication.response;

import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplication;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScholarshipApplicationResponseDTO(
        @Schema(description = "신청 ID") Long id,
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "장학금 유형") ScholarshipType type,
        @Schema(description = "신청 금액") BigDecimal requestedAmount,
        @Schema(description = "신청 사유") String reason,
        @Schema(description = "상태", allowableValues = {"REQUESTED", "APPROVED", "REJECTED"}) ScholarshipApplicationStatus status,
        @Schema(description = "심사자(Academic.users.id)") Long reviewedBy,
        @Schema(description = "심사 시각") LocalDateTime reviewedAt,
        @Schema(description = "반려 사유") String rejectReason,
        @Schema(description = "승인 시 생성된 장학금 ID") Long scholarshipId,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {
    public static ScholarshipApplicationResponseDTO from(ScholarshipApplication application) {
        return new ScholarshipApplicationResponseDTO(
                application.getId(), application.getTuitionBillId(), application.getType(),
                application.getRequestedAmount(), application.getReason(), application.getStatus(),
                application.getReviewedBy(), application.getReviewedAt(), application.getRejectReason(),
                application.getScholarshipId(), application.getCreatedAt());
    }
}
