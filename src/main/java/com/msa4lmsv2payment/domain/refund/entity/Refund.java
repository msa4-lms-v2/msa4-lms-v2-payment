package com.msa4lmsv2payment.domain.refund.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId;

    private Long virtualAccountId;

    private Long tuitionBillId;

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

    public void updateRate(BigDecimal amount, BigDecimal refundRate) {
        this.amount = amount;
        this.refundRate = refundRate;
    }

    public void linkVirtualAccount(Long virtualAccountId) {
        this.virtualAccountId = virtualAccountId;
    }

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
