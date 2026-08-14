package com.msa4lmsv2payment.global.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 두 가지 책임을 분리해서 갖는다.
 * 1) 매분: 선점(IN_PROGRESS) 후 만료 시간까지 완료되지 못한 키를 FAILED로 전환해 재시도를 막지 않는다.
 * 2) 매일: idempotency_keys는 물리 삭제 대상 테이블(ERD 1절)이므로, 생성된 지 오래된 행(상태 무관)을 지운다.
 *    완료/실패 상태를 재생하는 목적은 클라이언트가 재시도할 만한 기간 동안만 유효하면 충분하고, 그 기간이
 *    지나면 계속 쌓아둘 이유가 없다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyCleanupScheduler {

    private static final long RETENTION_DAYS = 7;

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Scheduled(cron = "0 * * * * *")
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

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupOldKeys() {
        long deleted = idempotencyKeyRepository.deleteByCreatedAtBefore(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("보존기간({}일)이 지난 idempotency_keys {}건 정리", RETENTION_DAYS, deleted);
        }
    }
}
