package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

/**
 * 이미 MANUAL_REVIEW_REQUIRED로 전환된 자퇴 환불 건에 재검증이 다시 실패한 경우.
 * 관리자 확인 전까지 PENDING_ACADEMIC_VERIFICATION으로 되돌리지 않는다.
 */
public class RefundManualReviewRequiredException extends BusinessException {

    public RefundManualReviewRequiredException(String message) {
        super(CustomResponseCode.MANUAL_REVIEW_REQUIRED, message);
    }
}
