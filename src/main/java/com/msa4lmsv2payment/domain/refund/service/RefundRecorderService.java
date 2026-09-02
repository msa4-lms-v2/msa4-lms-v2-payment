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
    public Refund saveRetried(Long actorId, Refund refund) {
        Refund saved = refundRepository.save(refund);
        auditLogRecorder.record(actorId, AuditAction.REFUND_RETRIED, "REFUND", saved.getId(),
                Map.of("retryCount", saved.getRetryCount()), null);
        return saved;
    }
}
