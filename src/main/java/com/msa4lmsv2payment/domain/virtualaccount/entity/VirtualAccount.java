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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    private Long installmentPlanItemId;

    private String paymentKey;

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

    // 분할납부 회차별 가상계좌 발급 - installmentPlanItemId가 채워진 계좌는 회차 금액만 순납부액으로 취급한다.
    public VirtualAccount(Long tuitionBillId, String orderId, String secret, String accountNumber, String bankCode,
                           LocalDateTime expiresAt, VirtualAccountStatus status, Long installmentPlanItemId) {
        this(tuitionBillId, orderId, secret, accountNumber, bankCode, expiresAt, status);
        this.installmentPlanItemId = installmentPlanItemId;
    }

    public boolean matchesSecret(String candidate) {
        return this.secret != null && candidate != null && MessageDigest.isEqual(
                this.secret.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    // 토스 가상계좌 발급 응답의 paymentKey - 발급 직후 한 번만 채워지며, 이후 이 계좌를 취소(환불)할 때 사용한다.
    public void assignPaymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
    }

    // 누적 입금액을 순납부액과 비교해 상태를 갱신한다. 초과분은 호출한 쪽이 환불로 처리한다.
    public void applyDeposit(BigDecimal totalDeposited, BigDecimal netDue) {
        if (totalDeposited.compareTo(netDue) >= 0) {
            this.status = VirtualAccountStatus.DEPOSITED;
        } else if (totalDeposited.compareTo(BigDecimal.ZERO) > 0) {
            this.status = VirtualAccountStatus.PARTIALLY_DEPOSITED;
        }
    }

    // 이미 완납된 계좌는 만료 스케줄러가 지나가도 내려가지 않는다(applyDeposit과 같은 단방향 원칙).
    public boolean expire() {
        if (this.status == VirtualAccountStatus.DEPOSITED) {
            return false;
        }
        this.status = VirtualAccountStatus.EXPIRED;
        return true;
    }
}
