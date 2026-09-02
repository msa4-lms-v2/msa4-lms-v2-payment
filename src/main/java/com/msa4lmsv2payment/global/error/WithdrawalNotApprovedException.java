package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class WithdrawalNotApprovedException extends BusinessException {

    public WithdrawalNotApprovedException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
