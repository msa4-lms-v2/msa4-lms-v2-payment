package com.msa4lmsv2payment.domain.refund.entity;

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
@Table(name = "refunds")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId;

    private Long virtualAccountId;

    private Long tuitionBillId;

    private Long withdrawalId;

    @Enumerated(EnumType.STRING)
    private RefundType refundType;

    private BigDecimal amount;

    private BigDecimal refundRate;

    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    @CreatedDate
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    private Integer retryCount = 0;

    public Refund(Long tuitionBillId, RefundType refundType, BigDecimal amount, BigDecimal refundRate, RefundStatus status) {
        this.tuitionBillId = tuitionBillId;
        this.refundType = refundType;
        this.amount = amount;
        this.refundRate = refundRate;
        this.status = status;
    }

    public void updateRate(Long withdrawalId, BigDecimal amount, BigDecimal refundRate) {
        if (status == RefundStatus.SUCCEEDED) {
            throw new IllegalStateException("완료된 환불 금액과 환불률은 변경할 수 없습니다.");
        }
        this.withdrawalId = withdrawalId;
        this.amount = amount;
        this.refundRate = refundRate;
    }

    public void linkVirtualAccount(Long virtualAccountId) {
        this.virtualAccountId = virtualAccountId;
    }

    // 입금 검증 인프라가 갖춰져 이 메서드를 호출하게 되면, 호출 지점에서
    // TuitionBillService의 납부상태 재계산도 함께 호출해야 한다 - 환불 완료가 tuition_bills.status에 반영되지 않으면
    // 이미 환불된 고지가 계속 PAID로 남는다.
    public void succeed() {
        this.status = RefundStatus.SUCCEEDED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = RefundStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public void retry() {
        this.retryCount = this.retryCount + 1;
        this.status = RefundStatus.RETRYING;
        this.completedAt = null;
    }
}
