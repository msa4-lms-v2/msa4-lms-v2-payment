package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class RefundRetryLimitExceededException extends BusinessException {

    public RefundRetryLimitExceededException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
