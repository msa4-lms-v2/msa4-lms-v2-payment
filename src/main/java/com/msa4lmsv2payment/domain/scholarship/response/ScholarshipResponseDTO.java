package com.msa4lmsv2payment.domain.scholarship.response;

import com.msa4lmsv2payment.domain.scholarship.entity.Scholarship;
import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScholarshipResponseDTO(
        Long id,
        Long tuitionBillId,
        ScholarshipType type,
        BigDecimal amount,
        String reason,
        Long approvedBy,
        LocalDateTime createdAt
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
