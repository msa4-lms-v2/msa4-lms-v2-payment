package com.msa4lmsv2payment.domain.virtualaccount.entity;

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
@Table(name = "virtual_account_deposits")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class VirtualAccountDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long virtualAccountId;

    private BigDecimal amount;

    private String tossTransactionKey;

    private LocalDateTime receivedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    public VirtualAccountDeposit(Long virtualAccountId, BigDecimal amount, String tossTransactionKey, LocalDateTime receivedAt) {
        this.virtualAccountId = virtualAccountId;
        this.amount = amount;
        this.tossTransactionKey = tossTransactionKey;
        this.receivedAt = receivedAt;
    }
}
