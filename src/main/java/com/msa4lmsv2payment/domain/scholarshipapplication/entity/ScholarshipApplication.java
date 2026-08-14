package com.msa4lmsv2payment.domain.scholarshipapplication.entity;

import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationAlreadyReviewedException;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scholarship_applications")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ScholarshipApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tuitionBillId;

    private Long studentId;

    @Enumerated(EnumType.STRING)
    private ScholarshipType type;

    private BigDecimal requestedAmount;

    private String reason;

    @Enumerated(EnumType.STRING)
    private ScholarshipApplicationStatus status;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String rejectReason;

    private Long scholarshipId;

    @CreatedDate
    private LocalDateTime createdAt;

    public ScholarshipApplication(Long tuitionBillId, Long studentId, ScholarshipType type,
                                   BigDecimal requestedAmount, String reason) {
        this.tuitionBillId = tuitionBillId;
        this.studentId = studentId;
        this.type = type;
        this.requestedAmount = requestedAmount;
        this.reason = reason;
        this.status = ScholarshipApplicationStatus.REQUESTED;
    }

    public void approve(Long reviewerId, Long scholarshipId) {
        requireReviewable();
        this.status = ScholarshipApplicationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.scholarshipId = scholarshipId;
    }

    public void reject(Long reviewerId, String rejectReason) {
        requireReviewable();
        this.status = ScholarshipApplicationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectReason = rejectReason;
    }

    private void requireReviewable() {
        if (status != ScholarshipApplicationStatus.REQUESTED) {
            throw new ScholarshipApplicationAlreadyReviewedException("이미 심사가 완료된 신청입니다.");
        }
    }
}
