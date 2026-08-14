package com.msa4lmsv2payment.domain.payment.entity;

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
@Table(name = "payments")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tuitionBillId;

    private Long studentId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Column(unique = true)
    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @CreatedDate
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    public Payment(Long tuitionBillId, Long studentId, BigDecimal amount, PaymentMethod method, PaymentStatus status) {
        this.tuitionBillId = tuitionBillId;
        this.studentId = studentId;
        this.amount = amount;
        this.method = method;
        this.status = status;
    }

    public void succeed(String pgTransactionId) {
        if (status == PaymentStatus.SUCCEEDED) {
            if (!this.pgTransactionId.equals(pgTransactionId)) {
                throw new IllegalStateException("성공 결제의 PG 거래 ID는 변경할 수 없습니다.");
            }
            return;
        }
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.SUCCEEDED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        if (status == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("성공 결제를 실패 상태로 되돌릴 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isSucceeded() {
        return status == PaymentStatus.SUCCEEDED;
    }
}
