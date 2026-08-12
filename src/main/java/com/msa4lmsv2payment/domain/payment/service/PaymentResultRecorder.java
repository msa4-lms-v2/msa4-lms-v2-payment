package com.msa4lmsv2payment.domain.payment.service;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentResultRecorder {

    private final PaymentRepository paymentRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public Payment saveWithAudit(Long actorId, Payment payment, String tossStatus) {
        Payment saved = paymentRepository.save(payment);
        AuditAction action = saved.isSucceeded() ? AuditAction.PAYMENT_APPROVED : AuditAction.PAYMENT_FAILED;
        Map<String, ?> afterValue = saved.isSucceeded()
                ? Map.of("tuitionBillId", saved.getTuitionBillId(), "amount", saved.getAmount())
                : Map.of("tossStatus", tossStatus == null ? "UNKNOWN" : tossStatus);
        auditLogRecorder.record(actorId, action, "PAYMENT", saved.getId(), afterValue, null);
        return saved;
    }
}
