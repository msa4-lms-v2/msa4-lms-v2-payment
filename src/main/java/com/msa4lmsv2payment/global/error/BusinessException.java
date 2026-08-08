package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public abstract class BusinessException extends RuntimeException {

    private final CustomResponseCode code;

    protected BusinessException(CustomResponseCode code, String message) {
        super(message);
        this.code = code;
    }

    public CustomResponseCode getCode() {
        return code;
    }
}
