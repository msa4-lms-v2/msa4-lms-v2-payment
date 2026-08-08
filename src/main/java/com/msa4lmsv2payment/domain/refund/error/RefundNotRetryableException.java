package com.msa4lmsv2payment.domain.refund.error;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class RefundNotRetryableException extends BusinessException {

    public RefundNotRetryableException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
