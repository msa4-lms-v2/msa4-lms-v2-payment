package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class IdempotencyKeyConflictException extends BusinessException {

    public IdempotencyKeyConflictException(String message) {
        super(CustomResponseCode.DUPLICATE_DATA, message);
    }
}
