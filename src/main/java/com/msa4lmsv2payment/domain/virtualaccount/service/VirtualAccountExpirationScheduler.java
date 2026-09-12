package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.scheduling.CronScheduling;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 발급 후 유효기간(expires_at)이 지났는데 아직 완납(DEPOSITED)되지 않은 가상계좌를 EXPIRED로 전환한다.
 * DEPOSITED는 이미 완납된 계좌라 만료 대상에서 제외한다(VirtualAccount.expire()의 단방향 원칙).
 *
 * <p>spring.threads.virtual.enabled=true 환경에서 Spring {@code @Scheduled}가 배포 pod에서 실행되지
 * 않는 현상이 확인돼(academic의 OutboxWorker 참고), 별도 ScheduledExecutorService로 직접 폴링한다.
 * expireOverdueAccounts()의 @Transactional은 AOP 프록시를 거쳐야 적용되므로, this로 직접 호출하지
 * 않고 지연 주입한 자기 자신(self)의 프록시를 통해 호출한다(self-invocation 우회).
 */
@Slf4j
@Component
public class VirtualAccountExpirationScheduler {

    private static final String CRON = "0 */10 * * * *";
    private static final List<VirtualAccountStatus> EXPIRABLE_STATUSES =
            List.of(VirtualAccountStatus.ISSUED, VirtualAccountStatus.PARTIALLY_DEPOSITED);
    // 스케줄러는 로그인 사용자가 없는 시스템 동작이라 감사 로그의 actor_id는 예약 값 0(SYSTEM)을 쓴다.
    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final VirtualAccountRepository virtualAccountRepository;
    private final AuditLogRecorder auditLogRecorder;
    private final VirtualAccountExpirationScheduler self;

    private ScheduledExecutorService scheduler;

    public VirtualAccountExpirationScheduler(VirtualAccountRepository virtualAccountRepository,
                                              AuditLogRecorder auditLogRecorder,
                                              @Lazy VirtualAccountExpirationScheduler self) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.auditLogRecorder = auditLogRecorder;
        this.self = self;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "virtual-account-expiration-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        CronScheduling.scheduleCron(scheduler, CRON, ZoneId.systemDefault(), this::runExpireOverdueAccounts);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void runExpireOverdueAccounts() {
        try {
            self.expireOverdueAccounts();
        } catch (Exception exception) {
            log.error("유효기간이 지난 가상계좌 EXPIRED 처리 중 예상치 못한 예외 발생", exception);
        }
    }

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
