package com.msa4lmsv2payment.global.scheduling;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.support.CronExpression;

/**
 * spring.threads.virtual.enabled=true 환경에서 Spring {@code @Scheduled}(SimpleAsyncTaskScheduler)가
 * 배포 pod에서 실행되지 않는 현상이 academic의 OutboxWorker에서 확인된 바 있어(2026-09-04, 커밋
 * 2fb3a70/a0cea71), cron 표현식이 필요한 스케줄러는 Spring 스케줄링 인프라 대신 이 유틸로 직접 다음
 * 실행 시각을 계산해 ScheduledExecutorService에 재귀 예약한다.
 */
public final class CronScheduling {

    // Spring @Scheduled(cron=...)와 동일하게 "-"는 비활성화로 취급한다.
    private static final String DISABLED = "-";

    private CronScheduling() {
    }

    public static void scheduleCron(ScheduledExecutorService executor, String cronExpression, ZoneId zone, Runnable task) {
        if (DISABLED.equals(cronExpression)) {
            return;
        }
        CronExpression cron = CronExpression.parse(cronExpression);
        scheduleNext(executor, cron, zone, task);
    }

    private static void scheduleNext(ScheduledExecutorService executor, CronExpression cron, ZoneId zone, Runnable task) {
        if (executor.isShutdown()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = cron.next(now);
        if (next == null) {
            return;
        }
        long delayMillis = Math.max(Duration.between(now, next).toMillis(), 0);
        executor.schedule(() -> {
            try {
                task.run();
            } finally {
                scheduleNext(executor, cron, zone, task);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }
}
