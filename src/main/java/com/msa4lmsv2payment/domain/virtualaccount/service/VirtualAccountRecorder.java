package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 4.6: 가상계좌 저장과 감사 로그 기록을 하나의 트랜잭션으로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class VirtualAccountRecorder {

    private final VirtualAccountRepository virtualAccountRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public VirtualAccount saveWithAudit(Long actorId, VirtualAccount virtualAccount) {
        VirtualAccount saved = virtualAccountRepository.save(virtualAccount);
        auditLogRecorder.record(actorId, AuditAction.VIRTUAL_ACCOUNT_ISSUED, "VIRTUAL_ACCOUNT", saved.getId(),
                Map.of("tuitionBillId", saved.getTuitionBillId(), "accountNumber", saved.getAccountNumber()), null);
        return saved;
    }
}
