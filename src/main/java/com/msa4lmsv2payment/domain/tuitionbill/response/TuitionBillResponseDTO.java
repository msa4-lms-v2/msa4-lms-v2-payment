package com.msa4lmsv2payment.domain.tuitionbill.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionBillResponseDTO(
        @Schema(description = "등록금 고지 ID") Long id,
        @Schema(description = "학생 내부 ID (학번과 별도)") Long studentId,
        @Schema(description = "Academic.semesters.id") Long semesterId,
        @Schema(description = "고지 금액") BigDecimal billingAmount,
        @Schema(description = "납부 기한") LocalDate dueDate,
        @Schema(description = "납부 상태") TuitionBillStatus status,
        @Schema(description = "입학 예정자 ID", nullable = true) Long admissionCandidateId,
        @Schema(description = "입학 고지 대상자 이름", nullable = true) String admissionCustomerName
) {
    public static TuitionBillResponseDTO from(TuitionBill tuitionBill) {
        return new TuitionBillResponseDTO(
                tuitionBill.getId(),
                tuitionBill.getStudentId(),
                tuitionBill.getSemesterId(),
                tuitionBill.getBillingAmount(),
                tuitionBill.getDueDate(),
                tuitionBill.getStatus(),
                tuitionBill.getAdmissionCandidateId(),
                tuitionBill.getAdmissionCustomerName()
        );
    }
}
