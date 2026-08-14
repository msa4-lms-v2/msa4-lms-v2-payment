package com.msa4lmsv2payment.global.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    List<IdempotencyKey> findByStatusAndExpiresAtBefore(IdempotencyKeyStatus status, LocalDateTime cutoff);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
