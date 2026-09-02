package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class TossServiceUnavailableException extends BusinessException {

    public TossServiceUnavailableException(String message) {
        super(CustomResponseCode.DEPENDENCY_UNAVAILABLE, message);
    }
}
