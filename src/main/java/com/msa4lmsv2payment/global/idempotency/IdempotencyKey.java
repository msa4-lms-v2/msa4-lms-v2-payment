package com.msa4lmsv2payment.global.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * M2번(멱등성 키 소유) - Payment의 API가 중복 처리되지 않게 보장하는 Payment 자신의 책임.
 * idempotency_keys는 이력·임시 데이터 성격이라 소프트 삭제 대상이 아니다(ERD 1절 공통 규칙).
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String idempotencyKey;

    private Long requesterStudentId;

    private String endpoint;

    private String requestHash;

    private String responseSnapshot;

    @Enumerated(EnumType.STRING)
    private IdempotencyKeyStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    public IdempotencyKey(String idempotencyKey, Long requesterStudentId, String endpoint, String requestHash,
                           IdempotencyKeyStatus status, LocalDateTime expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.requesterStudentId = requesterStudentId;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public void complete(String responseSnapshot) {
        this.responseSnapshot = responseSnapshot;
        this.status = IdempotencyKeyStatus.COMPLETED;
    }

    public void fail(String responseSnapshot) {
        this.responseSnapshot = responseSnapshot;
        this.status = IdempotencyKeyStatus.FAILED;
    }

    public void requireReconciliation(String responseSnapshot) {
        this.responseSnapshot = responseSnapshot;
        this.status = IdempotencyKeyStatus.RECONCILIATION_REQUIRED;
    }

    public void restart(LocalDateTime expiresAt) {
        this.responseSnapshot = null;
        this.status = IdempotencyKeyStatus.IN_PROGRESS;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
