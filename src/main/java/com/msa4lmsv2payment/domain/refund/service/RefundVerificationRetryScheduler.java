package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 자퇴 환불률 산정 장애 격리("장애 격리" 절, ARCHITECTURE.md) - PENDING_ACADEMIC_VERIFICATION으로 보류된 환불을
 * 주기적으로 재검증한다. 성공하면 REQUESTED로 확정하고, MANUAL_REVIEW_THRESHOLD_HOURS를 넘겨도 해소되지 않으면
 * MANUAL_REVIEW_REQUIRED로 전환한다.
 *
 * <p>폴링 주기(기본 10분)는 payment의 다른 스케줄러(OverdueTransitionScheduler, "0 * /10 * * * *")와 동일한
 * 주기로 맞췄다. 유예 시간(24시간)은 AcademicResyncClient가 문서화한 "Kafka 24시간 보관"을 넘겨도 스냅샷에
 * 반영되지 않으면 이벤트가 이미 유실된 것으로 보고 자동 재시도를 멈춘다는 근거에서 그대로 가져왔다.
 *
 * <p>spring.threads.virtual.enabled=true 환경에서 Spring {@code @Scheduled}(SimpleAsyncTaskScheduler)가
 * 배포 환경에서 실행되지 않는 현상이 msa4-lms-v2-academic의 OutboxWorker에서 확인된 바 있다(2026-09-04,
 * 커밋 2fb3a70/a0cea71). payment도 같은 virtual thread 설정을 쓰고 있어 동일한 위험에 노출돼 있으므로,
 * 이 스케줄러는 Spring 스케줄링 인프라를 거치지 않는 별도 ScheduledExecutorService로 직접 폴링한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundVerificationRetryScheduler {

    private static final long MANUAL_REVIEW_THRESHOLD_HOURS = 24;

    private final RefundService refundService;

    @Value("${payment.refund.verification-retry.poll-interval-ms:600000}")
    private long pollIntervalMs;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "refund-verification-retry-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::retryPendingVerifications, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        log.info("RefundVerificationRetryScheduler 폴링 시작 (interval={}ms)", pollIntervalMs);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // ScheduledExecutorService는 Runnable에서 예외가 새어나가면 이후 스케줄이 조용히 멈추는 계약이라
    // 반드시 이 메서드 안에서 모든 예외를 잡는다(OutboxWorker와 동일 원칙).
    private void retryPendingVerifications() {
        try {
            processPendingVerifications();
        } catch (Exception exception) {
            log.error("자퇴 환불률 재검증 폴링 중 예상치 못한 예외 발생", exception);
        }
    }

    private void processPendingVerifications() {
        List<Refund> pending = refundService.findPendingAcademicVerifications();
        if (pending.isEmpty()) {
            return;
        }

        LocalDateTime deadline = LocalDateTime.now().minusHours(MANUAL_REVIEW_THRESHOLD_HOURS);
        int resolved = 0;
        int escalated = 0;
        for (Refund refund : pending) {
            boolean verified;
            try {
                verified = refundService.retryPendingAcademicVerification(refund.getId());
            } catch (Exception exception) {
                log.warn("환불(id={}) 재검증 중 예외 발생, 이번 주기는 건너뛴다", refund.getId(), exception);
                continue;
            }
            if (verified) {
                resolved++;
                continue;
            }
            if (refund.getRequestedAt().isBefore(deadline)) {
                refundService.escalateToManualReview(refund.getId());
                escalated++;
            }
        }
        if (resolved > 0 || escalated > 0) {
            log.info("자퇴 환불률 재검증 {}건 확정, {}건 수동 검토 전환", resolved, escalated);
        }
    }
}
