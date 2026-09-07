package com.msa4lmsv2payment.domain.payment.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Academic이 학생·학기별 등록금 납부 상태를 조회할 때 쓰는 응답(SCRUM-176).
 * docs-v2/MSA-LMS_INTEGRATION.md "Academic → Payment 조회" 계약과 짝을 이룬다.
 */
public record AcademicTuitionStatusResponseDTO(
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "고지 금액") BigDecimal billingAmount,
        @Schema(description = "장학금 합계") BigDecimal totalScholarshipAmount,
        @Schema(description = "순납부액(성공 결제 합계 - 성공 환불 합계)") BigDecimal totalPaid,
        @Schema(description = "잔액") BigDecimal remainingAmount,
        @Schema(description = "납부 상태", allowableValues = {"UNPAID", "PARTIAL", "PAID", "OVERDUE"}) TuitionBillStatus status,
        @Schema(description = "진행 중인 환불(REQUESTED 또는 RETRYING) 존재 여부 - true면 status가 아직 최종 확정이 아닐 수 있음") boolean hasPendingRefund
) {
}
