package com.msa4lmsv2payment.global.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * idempotency_keys는 물리 삭제 대상 테이블(ERD 1절)인데 만료된 행을 지우는 경로가 없어 무한정 쌓이고 있었다.
 * expires_at이 지난 행을 매일 지운다 - IN_PROGRESS/COMPLETED 상태와 무관하게 만료 여부로만 판단한다
 * (IdempotencyService.verifyAndReserve도 상태가 아니라 isExpired()로 재시도 허용 여부를 판단하는 것과 동일한 기준).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyCleanupScheduler {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupExpiredKeys() {
        long deleted = idempotencyKeyRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("만료된 idempotency_keys {}건 정리", deleted);
        }
    }
}
