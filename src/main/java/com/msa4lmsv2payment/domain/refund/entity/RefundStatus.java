package com.msa4lmsv2payment.domain.refund.entity;

public enum RefundStatus {
    REQUESTED,
    SUCCEEDED,
    FAILED,
    RETRYING,
    // Academic의 withdrawal_snapshots에 아직 자퇴 건이 반영되지 않아 환불률을 계산할 수 없는 보류 상태.
    // RefundVerificationRetryScheduler가 자동 재검증한다("장애 격리" 절, ARCHITECTURE.md).
    PENDING_ACADEMIC_VERIFICATION,
    // PENDING_ACADEMIC_VERIFICATION이 재검증 유예 시간을 넘겨도 해소되지 않아 관리자 확인이 필요한 상태.
    MANUAL_REVIEW_REQUIRED
}
