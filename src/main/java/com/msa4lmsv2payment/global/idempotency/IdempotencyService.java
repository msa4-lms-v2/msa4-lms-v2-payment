package com.msa4lmsv2payment.global.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * M2번(멱등성 키 소유) - Idempotency-Key 헤더 검증을 각 서비스가 직접 담당한다.
 * 캐시된 응답을 그대로 재생하지는 않는다 - 대신 같은 키+같은 요청이면 하위 로직을 다시 태우는 것을 허용한다
 * (refunds/virtual_accounts 쪽 로직 자체가 이미 upsert 성격이라 재실행해도 결과가 같다).
 * 다른 요청에 같은 키를 재사용하면 거부한다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final long EXPIRES_IN_DAYS = 1;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void verifyAndReserve(String idempotencyKey, Long requesterId, String endpoint, Object requestBody) {
        String requestHash = hash(requestBody);

        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty()) {
            idempotencyKeyRepository.save(new IdempotencyKey(
                    idempotencyKey, requesterId, endpoint, requestHash,
                    IdempotencyKeyStatus.IN_PROGRESS, LocalDateTime.now().plusDays(EXPIRES_IN_DAYS)));
            return;
        }

        IdempotencyKey key = existing.orElseThrow();
        if (!key.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException("이미 다른 요청에 사용된 Idempotency-Key입니다.");
        }
        if (key.getStatus() == IdempotencyKeyStatus.IN_PROGRESS && !key.isExpired()) {
            throw new IdempotencyKeyConflictException("동일한 요청이 이미 처리 중입니다.");
        }
        // COMPLETED거나 IN_PROGRESS 상태로 만료된 경우(이전 시도가 중간에 실패한 것으로 간주) 재시도를 허용한다.
    }

    @Transactional
    public void markCompleted(String idempotencyKey) {
        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey).ifPresent(IdempotencyKey::complete);
    }

    private String hash(Object requestBody) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
