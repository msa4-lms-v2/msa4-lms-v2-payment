package com.msa4lmsv2payment.global.idempotency;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 두 가지 책임을 분리해서 갖는다.
 * 1) 매분: 선점(IN_PROGRESS) 후 만료 시간까지 완료되지 못한 키를 FAILED로 전환해 재시도를 막지 않는다.
 * 2) 매일: idempotency_keys는 이력·임시 데이터라 소프트 삭제 대상이 아니므로, 생성된 지 오래된 행(상태 무관)을 지운다.
 *    완료/실패 상태를 재생하는 목적은 클라이언트가 재시도할 만한 기간 동안만 유효하면 충분하고, 그 기간이
 *    지나면 계속 쌓아둘 이유가 없다.
 *
 * <p>spring.threads.virtual.enabled=true 환경에서 Spring {@code @Scheduled}가 배포 pod에서 실행되지
 * 않는 현상이 확인돼(academic의 OutboxWorker 참고), 별도 ScheduledExecutorService로 직접 폴링한다.
 * 각 정리 메서드의 @Transactional은 AOP 프록시를 거쳐야 적용되므로, this로 직접 호출하지 않고
 * 지연 주입한 자기 자신(self)의 프록시를 통해 호출한다(self-invocation 우회).
 */
@Component
@Slf4j
public class IdempotencyKeyCleanupScheduler {

    private static final long RETENTION_DAYS = 7;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final IdempotencyKeyCleanupScheduler self;

    private ScheduledExecutorService scheduler;

    public IdempotencyKeyCleanupScheduler(IdempotencyKeyRepository idempotencyKeyRepository,
                                           @Lazy IdempotencyKeyCleanupScheduler self) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.self = self;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "idempotency-key-cleanup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        CronScheduling.scheduleCron(scheduler, "0 * * * * *", ZoneId.systemDefault(), this::runRecoverExpiredKeys);
        CronScheduling.scheduleCron(scheduler, "0 0 4 * * *", ZoneId.systemDefault(), this::runCleanupOldKeys);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void runRecoverExpiredKeys() {
        try {
            self.recoverExpiredKeys();
        } catch (Exception exception) {
            log.error("만료된 IN_PROGRESS 멱등키 정리 중 예상치 못한 예외 발생", exception);
        }
    }

    private void runCleanupOldKeys() {
        try {
            self.cleanupOldKeys();
        } catch (Exception exception) {
            log.error("보존기간이 지난 idempotency_keys 정리 중 예상치 못한 예외 발생", exception);
        }
    }

    @Transactional
    public void recoverExpiredKeys() {
        List<IdempotencyKey> expiredKeys = idempotencyKeyRepository.findByStatusAndExpiresAtBefore(
                IdempotencyKeyStatus.IN_PROGRESS, LocalDateTime.now());
        for (IdempotencyKey key : expiredKeys) {
            key.fail("{\"error\": \"TIMEOUT\", \"message\": \"처리 중 타임아웃되었습니다.\"}");
        }
        if (!expiredKeys.isEmpty()) {
            log.info("만료된 IN_PROGRESS 멱등키 {}건 FAILED 처리", expiredKeys.size());
        }
    }

    @Transactional
    public void cleanupOldKeys() {
        long deleted = idempotencyKeyRepository.deleteByCreatedAtBefore(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("보존기간({}일)이 지난 idempotency_keys {}건 정리", RETENTION_DAYS, deleted);
        }
    }
}
