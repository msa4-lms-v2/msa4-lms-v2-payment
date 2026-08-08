package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class TossServiceUnavailableException extends BusinessException {

    public TossServiceUnavailableException(String message) {
        super(CustomResponseCode.TOSS_SERVICE_UNAVAILABLE, message);
    }
}
