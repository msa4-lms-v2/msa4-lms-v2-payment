package com.msa4lmsv2payment.global.idempotency;

public enum IdempotencyKeyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    RECONCILIATION_REQUIRED
}
