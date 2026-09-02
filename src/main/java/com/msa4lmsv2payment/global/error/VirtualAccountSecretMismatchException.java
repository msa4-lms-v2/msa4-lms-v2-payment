package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class VirtualAccountSecretMismatchException extends BusinessException {

    public VirtualAccountSecretMismatchException(String message) {
        super(CustomResponseCode.ACCESS_DENIED, message);
    }
}
