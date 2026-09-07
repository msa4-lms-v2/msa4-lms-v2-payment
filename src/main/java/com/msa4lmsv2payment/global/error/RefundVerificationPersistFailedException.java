package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

/**
 * PENDING_ACADEMIC_VERIFICATION 보류 상태 자체를 DB에 저장하지 못한 경우(DB 접근 불가 등).
 * "장애 격리" 절 원칙대로 보류를 저장한 경우에만 202+PENDING_*을 반환할 수 있으므로,
 * 저장이 실패하면 503과 Retry-After로 클라이언트가 재시도하게 한다.
 */
public class RefundVerificationPersistFailedException extends BusinessException {

    private final long retryAfterSeconds;

    public RefundVerificationPersistFailedException(String message, long retryAfterSeconds) {
        super(CustomResponseCode.DEPENDENCY_UNAVAILABLE, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
