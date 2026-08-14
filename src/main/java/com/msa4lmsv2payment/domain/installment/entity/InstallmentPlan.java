package com.msa4lmsv2payment.domain.installment.entity;

import com.msa4lmsv2payment.global.error.InstallmentPlanAlreadyReviewedException;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "installment_plans")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class InstallmentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tuitionBillId;

    private Integer totalRounds;

    @Enumerated(EnumType.STRING)
    private InstallmentPlanStatus status;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String rejectReason;

    @CreatedDate
    private LocalDateTime createdAt;

    public InstallmentPlan(Long tuitionBillId, Integer totalRounds) {
        this.tuitionBillId = tuitionBillId;
        this.totalRounds = totalRounds;
        this.status = InstallmentPlanStatus.REQUESTED;
    }

    // ADMIN 승인 전에는 회차 결제(체크아웃 세션 생성)를 할 수 없다 - 신청만으로는 분할납부를 시작할 수 없다.
    public void approve(Long reviewerId) {
        requireReviewable();
        this.status = InstallmentPlanStatus.ACTIVE;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Long reviewerId, String rejectReason) {
        requireReviewable();
        this.status = InstallmentPlanStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectReason = rejectReason;
    }

    public void complete() {
        this.status = InstallmentPlanStatus.COMPLETED;
    }

    private void requireReviewable() {
        if (status != InstallmentPlanStatus.REQUESTED) {
            throw new InstallmentPlanAlreadyReviewedException("이미 심사가 완료된 분할납부 신청입니다.");
        }
    }
}
