package com.msa4lmsv2payment.domain.tuitionbill.service;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 4.6: 등록금 고지 저장과 감사 로그 기록을 하나의 트랜잭션으로 묶는다.
 * self-invocation 문제를 피하려고 별도 Bean으로 분리했다(PaymentResultRecorder와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class TuitionBillRecorder {

    private final TuitionBillRepository tuitionBillRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public TuitionBill saveWithAudit(Long actorId, TuitionBill tuitionBill) {
        TuitionBill saved = tuitionBillRepository.save(tuitionBill);
        auditLogRecorder.record(actorId, AuditAction.TUITION_BILL_CREATED, "TUITION_BILL", saved.getId(),
                Map.of("studentId", saved.getStudentId(), "billingAmount", saved.getBillingAmount()), null);
        return saved;
    }
}
