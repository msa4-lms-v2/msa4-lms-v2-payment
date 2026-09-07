package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class RefundAmountExceedsPaymentException extends BusinessException {

    public RefundAmountExceedsPaymentException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
