package com.msa4lmsv2payment.domain.tuitionbill.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "tuition_bill_items")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TuitionBillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tuitionBillId;

    private String itemName;

    private BigDecimal amount;

    private boolean paid;

    @CreatedDate
    private LocalDateTime createdAt;

    public TuitionBillItem(Long tuitionBillId, String itemName, BigDecimal amount) {
        this.tuitionBillId = tuitionBillId;
        this.itemName = itemName;
        this.amount = amount;
        this.paid = false;
    }

    public void markPaid() {
        this.paid = true;
    }
}
