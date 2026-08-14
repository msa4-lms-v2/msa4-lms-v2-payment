package com.msa4lmsv2payment.domain.installment.response;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record InstallmentPlanResponseDTO(
        @Schema(description = "분할납부 계획 ID") Long id,
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "총 회차 수") Integer totalRounds,
        @Schema(description = "계획 상태", allowableValues = {"REQUESTED", "ACTIVE", "REJECTED", "COMPLETED"}) InstallmentPlanStatus status,
        @Schema(description = "심사자(Academic.users.id)") Long reviewedBy,
        @Schema(description = "심사 시각") LocalDateTime reviewedAt,
        @Schema(description = "반려 사유") String rejectReason,
        @Schema(description = "회차별 항목") List<InstallmentPlanItemResponseDTO> items
) {
    public static InstallmentPlanResponseDTO from(InstallmentPlan plan, List<InstallmentPlanItemResponseDTO> items) {
        return new InstallmentPlanResponseDTO(plan.getId(), plan.getTuitionBillId(), plan.getTotalRounds(), plan.getStatus(),
                plan.getReviewedBy(), plan.getReviewedAt(), plan.getRejectReason(), items);
    }
}
