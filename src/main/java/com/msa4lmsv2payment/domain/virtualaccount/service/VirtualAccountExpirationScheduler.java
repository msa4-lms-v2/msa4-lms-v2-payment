package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 발급 후 유효기간(expires_at)이 지났는데 아직 완납(DEPOSITED)되지 않은 가상계좌를 EXPIRED로 전환한다.
 * DEPOSITED는 이미 완납된 계좌라 만료 대상에서 제외한다(VirtualAccount.expire()의 단방향 원칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualAccountExpirationScheduler {

    private static final List<VirtualAccountStatus> EXPIRABLE_STATUSES =
            List.of(VirtualAccountStatus.ISSUED, VirtualAccountStatus.PARTIALLY_DEPOSITED);
    // 스케줄러는 로그인 사용자가 없는 시스템 동작이라 감사 로그의 actor_id는 예약 값 0(SYSTEM)을 쓴다.
    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final VirtualAccountRepository virtualAccountRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void expireOverdueAccounts() {
        List<VirtualAccount> targets = virtualAccountRepository.findByStatusInAndExpiresAtBefore(
                EXPIRABLE_STATUSES, LocalDateTime.now());

        int expiredCount = 0;
        for (VirtualAccount account : targets) {
            if (account.expire()) {
                expiredCount++;
                auditLogRecorder.record(SYSTEM_ACTOR_ID, AuditAction.VIRTUAL_ACCOUNT_EXPIRED, "VIRTUAL_ACCOUNT", account.getId(),
                        Map.of("tuitionBillId", account.getTuitionBillId(), "expiresAt", account.getExpiresAt()), null);
            }
        }
        if (expiredCount > 0) {
            log.info("유효기간이 지난 가상계좌 {}건 EXPIRED 처리", expiredCount);
        }
    }
}
