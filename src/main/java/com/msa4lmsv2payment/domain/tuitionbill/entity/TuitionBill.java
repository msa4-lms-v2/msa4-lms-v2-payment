package com.msa4lmsv2payment.domain.tuitionbill.entity;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tuition_bills")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TuitionBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;

    private Long semesterId;

    private BigDecimal billingAmount;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private TuitionBillStatus status;

    private Long createdBy;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public TuitionBill(Long studentId, Long semesterId, BigDecimal billingAmount, LocalDate dueDate,
                        TuitionBillStatus status, Long createdBy) {
        this.studentId = studentId;
        this.semesterId = semesterId;
        this.billingAmount = billingAmount;
        this.dueDate = dueDate;
        this.status = status;
        this.createdBy = createdBy;
    }

    public void changeStatus(TuitionBillStatus status) {
        this.status = status;
    }
}
