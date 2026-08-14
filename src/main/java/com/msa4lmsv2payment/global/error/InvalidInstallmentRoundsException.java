package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class InvalidInstallmentRoundsException extends BusinessException {

    public InvalidInstallmentRoundsException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
