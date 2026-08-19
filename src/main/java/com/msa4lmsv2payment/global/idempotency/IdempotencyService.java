package com.msa4lmsv2payment.global.idempotency;

import com.msa4lmsv2payment.global.error.IdempotencyKeyConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * M2번(멱등성 키 소유) - Idempotency-Key 헤더 검증을 각 서비스가 직접 담당한다.
 * 완료된 같은 키+같은 요청은 저장한 응답을 재생하고 하위 로직을 다시 실행하지 않는다.
 * 요청자, endpoint 또는 payload가 다르면 같은 키를 사용할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final long EXPIRES_IN_MINUTES = 5;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> Optional<T> verifyAndReserve(String idempotencyKey, Long requesterId, String endpoint,
                                             Object requestBody, Class<T> responseType) {
        validateKey(idempotencyKey);
        String requestHash = hash(requestBody);

        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty()) {
            try {
                idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(
                        idempotencyKey, requesterId, endpoint, requestHash,
                        IdempotencyKeyStatus.IN_PROGRESS, LocalDateTime.now().plusMinutes(EXPIRES_IN_MINUTES)));
                return Optional.empty();
            } catch (DataIntegrityViolationException e) {
                throw new IdempotencyKeyConflictException("동일한 Idempotency-Key가 동시에 사용되었습니다.");
            }
        }

        IdempotencyKey key = existing.orElseThrow();
        if (!key.getRequesterStudentId().equals(requesterId)
                || !key.getEndpoint().equals(endpoint)
                || !key.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException("이미 다른 요청에 사용된 Idempotency-Key입니다.");
        }
        if (key.getStatus() == IdempotencyKeyStatus.COMPLETED) {
            if (key.getResponseSnapshot() == null || key.getResponseSnapshot().isBlank()) {
                throw new IdempotencyKeyConflictException("응답을 복원할 수 없는 Idempotency-Key입니다.");
            }
            return Optional.of(deserialize(key.getResponseSnapshot(), responseType));
        }
        if (key.getStatus() == IdempotencyKeyStatus.RECONCILIATION_REQUIRED) {
            throw new IdempotencyKeyConflictException("이전 요청 결과를 확인하는 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        if (key.getStatus() == IdempotencyKeyStatus.IN_PROGRESS && !key.isExpired()) {
            throw new IdempotencyKeyConflictException("동일한 요청이 이미 처리 중입니다.");
        }
        // IN_PROGRESS(만료) 또는 FAILED는 재시도를 허용한다 - FAILED를 COMPLETED처럼 영구 재생하면
        // 일시적 실패 이후 사용자가 같은 키로 다시는 성공할 수 없게 된다.
        key.restart(LocalDateTime.now().plusMinutes(EXPIRES_IN_MINUTES));
        return Optional.empty();
    }

    @Transactional
    public void markCompleted(String idempotencyKey, Object response) {
        String snapshot = serialize(response);
        IdempotencyKey key = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IdempotencyKeyConflictException("예약되지 않은 Idempotency-Key입니다."));
        key.complete(snapshot);
    }

    @Transactional
    public void markFailed(String idempotencyKey, Object response) {
        String snapshot = serialize(response);
        IdempotencyKey key = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IdempotencyKeyConflictException("예약되지 않은 Idempotency-Key입니다."));
        key.fail(snapshot);
    }

    @Transactional
    public void markReconciliationRequired(String idempotencyKey, Object response) {
        String snapshot = serialize(response);
        IdempotencyKey key = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IdempotencyKeyConflictException("예약되지 않은 Idempotency-Key입니다."));
        key.requireReconciliation(snapshot);
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IdempotencyKeyConflictException("Idempotency-Key는 1~100자의 값이어야 합니다.");
        }
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("멱등 응답을 저장할 수 없습니다.", e);
        }
    }

    private <T> T deserialize(String snapshot, Class<T> responseType) {
        try {
            return objectMapper.readValue(snapshot, responseType);
        } catch (Exception e) {
            throw new IllegalStateException("멱등 응답을 복원할 수 없습니다.", e);
        }
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
