package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 환불 상태 저장과 감사 로그 기록을 하나의 트랜잭션으로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class RefundRecorderService {

    private final RefundRepository refundRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public Refund saveRateApplied(Long actorId, Refund refund, Long tuitionBillId, BigDecimal amount, BigDecimal rate) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_REQUESTED, "REFUND", saved.getId(),
                Map.of("tuitionBillId", tuitionBillId, "withdrawalId", saved.getWithdrawalId(),
                        "amount", amount, "refundRate", rate),
                null);
        return saved;
    }

    @Transactional
    public Refund saveExcessDepositRefund(Long actorId, Refund refund) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_REQUESTED, "REFUND", saved.getId(),
                Map.of("tuitionBillId", saved.getTuitionBillId(), "virtualAccountId", saved.getVirtualAccountId(),
                        "amount", saved.getAmount()),
                null);
        return saved;
    }

    @Transactional
    public Refund saveRetried(Long actorId, Refund refund) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_RETRIED, "REFUND", saved.getId(),
                Map.of("retryCount", saved.getRetryCount()), null);
        return saved;
    }

    @Transactional
    public Refund savePgCancelRequested(Long actorId, Refund refund) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_REQUESTED, "REFUND", saved.getId(),
                Map.of("tuitionBillId", saved.getTuitionBillId(), "paymentId", saved.getPaymentId(), "amount", saved.getAmount()),
                null);
        return saved;
    }

    @Transactional
    public Refund saveSucceeded(Long actorId, Refund refund) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_SUCCEEDED, "REFUND", saved.getId(),
                Map.of("amount", saved.getAmount()), null);
        return saved;
    }

    @Transactional
    public Refund saveFailed(Long actorId, Refund refund, String reason) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_FAILED, "REFUND", saved.getId(),
                Map.of("amount", saved.getAmount()), reason);
        return saved;
    }

    @Transactional
    public Refund savePendingAcademicVerification(Long actorId, Refund refund, Long tuitionBillId) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_PENDING_ACADEMIC_VERIFICATION, "REFUND", saved.getId(),
                Map.of("tuitionBillId", tuitionBillId, "withdrawalId", saved.getWithdrawalId()),
                null);
        return saved;
    }

    @Transactional
    public Refund saveManualReviewRequired(Long actorId, Refund refund, String reason) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_MANUAL_REVIEW_REQUIRED, "REFUND", saved.getId(),
                Map.of("tuitionBillId", saved.getTuitionBillId(), "withdrawalId", saved.getWithdrawalId()),
                reason);
        return saved;
    }
}
