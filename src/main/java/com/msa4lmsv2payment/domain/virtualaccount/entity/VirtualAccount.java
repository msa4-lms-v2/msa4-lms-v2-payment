package com.msa4lmsv2payment.domain.virtualaccount.entity;

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
@Table(name = "virtual_accounts")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tuitionBillId;

    private String accountNumber;

    private String bankCode;

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private VirtualAccountStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    public VirtualAccount(Long tuitionBillId, String accountNumber, String bankCode, LocalDateTime expiresAt, VirtualAccountStatus status) {
        this.tuitionBillId = tuitionBillId;
        this.accountNumber = accountNumber;
        this.bankCode = bankCode;
        this.expiresAt = expiresAt;
        this.status = status;
    }
}
