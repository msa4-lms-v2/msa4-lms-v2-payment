package com.msa4lmsv2payment.domain.scholarship.response;

import com.msa4lmsv2payment.domain.scholarship.entity.Scholarship;
import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScholarshipResponseDTO(
        @Schema(description = "장학금 ID") Long id,
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "장학금 유형") ScholarshipType type,
        @Schema(description = "장학금 금액") BigDecimal amount,
        @Schema(description = "사유") String reason,
        @Schema(description = "승인자(Academic.users.id)") Long approvedBy,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {
    public static ScholarshipResponseDTO from(Scholarship scholarship) {
        return new ScholarshipResponseDTO(
                scholarship.getId(),
                scholarship.getTuitionBillId(),
                scholarship.getType(),
                scholarship.getAmount(),
                scholarship.getReason(),
                scholarship.getApprovedBy(),
                scholarship.getCreatedAt()
        );
    }
}
