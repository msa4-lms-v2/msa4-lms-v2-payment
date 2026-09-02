package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class RejectReasonRequiredException extends BusinessException {

    public RejectReasonRequiredException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
