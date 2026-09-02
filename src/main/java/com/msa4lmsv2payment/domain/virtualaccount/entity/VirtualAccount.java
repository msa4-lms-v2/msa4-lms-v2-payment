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

import java.math.BigDecimal;
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

    private String orderId;

    private String secret;

    private String accountNumber;

    private String bankCode;

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private VirtualAccountStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    public VirtualAccount(Long tuitionBillId, String orderId, String secret, String accountNumber, String bankCode,
                           LocalDateTime expiresAt, VirtualAccountStatus status) {
        this.tuitionBillId = tuitionBillId;
        this.orderId = orderId;
        this.secret = secret;
        this.accountNumber = accountNumber;
        this.bankCode = bankCode;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public boolean matchesSecret(String candidate) {
        return this.secret.equals(candidate);
    }

    // 누적 입금액을 순납부액과 비교해 상태를 갱신한다. 초과분은 호출한 쪽이 환불로 처리한다.
    public void applyDeposit(BigDecimal totalDeposited, BigDecimal netDue) {
        if (totalDeposited.compareTo(netDue) >= 0) {
            this.status = VirtualAccountStatus.DEPOSITED;
        } else if (totalDeposited.compareTo(BigDecimal.ZERO) > 0) {
            this.status = VirtualAccountStatus.PARTIALLY_DEPOSITED;
        }
    }
}
