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
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.SUCCEEDED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
