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

    private String refundBankCode;

    private String refundAccountNumber;

    private String refundHolderName;

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

    public void linkPayment(Long paymentId) {
        this.paymentId = paymentId;
    }

    // 가상계좌 환불(WITHDRAWAL/EXCESS_DEPOSIT) 실행 시 토스 cancel API의 refundReceiveAccount로 보낼 수취 계좌.
    // PG_CANCEL(카드)은 필요 없다.
    public void linkRefundReceiveAccount(String bankCode, String accountNumber, String holderName) {
        this.refundBankCode = bankCode;
        this.refundAccountNumber = accountNumber;
        this.refundHolderName = holderName;
    }

    // TuitionBillService/PaymentService의 납부상태 재계산은 호출부(RefundExecutionService)가 이어서 부른다 -
    // 그러지 않으면 환불 완료가 tuition_bills.status에 반영되지 않아 이미 환불된 고지가 계속 PAID로 남는다.
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

    // Academic 스냅샷에 자퇴 건이 아직 반영되지 않아 환불률을 계산할 수 없을 때 보류 상태로 저장한다.
    // amount/refundRate는 아직 계산 전이라 호출부가 0으로 채워 넣는다(NOT NULL 컬럼).
    public void markPendingAcademicVerification(Long withdrawalId) {
        if (status == RefundStatus.SUCCEEDED) {
            throw new IllegalStateException("완료된 환불은 상태를 변경할 수 없습니다.");
        }
        this.withdrawalId = withdrawalId;
        this.status = RefundStatus.PENDING_ACADEMIC_VERIFICATION;
    }

    // PENDING_ACADEMIC_VERIFICATION/MANUAL_REVIEW_REQUIRED 상태에서 재검증이 성공했을 때만 쓴다.
    // REQUESTED/FAILED/RETRYING 상태의 금액 갱신은 기존 updateRate를 그대로 쓴다(이 메서드로 상태를 강제로
    // REQUESTED로 되돌리지 않기 위해 분리했다).
    public void confirmAcademicVerification(Long withdrawalId, BigDecimal amount, BigDecimal refundRate) {
        if (status == RefundStatus.SUCCEEDED) {
            throw new IllegalStateException("완료된 환불 금액과 환불률은 변경할 수 없습니다.");
        }
        this.withdrawalId = withdrawalId;
        this.amount = amount;
        this.refundRate = refundRate;
        this.status = RefundStatus.REQUESTED;
    }

    // PENDING_ACADEMIC_VERIFICATION이 재검증 유예 시간을 넘겨도 해소되지 않을 때 관리자 확인으로 넘긴다.
    public void requireManualReview() {
        this.status = RefundStatus.MANUAL_REVIEW_REQUIRED;
    }
}
