package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class InstallmentPlanAlreadyExistsException extends BusinessException {

    public InstallmentPlanAlreadyExistsException(String message) {
        super(CustomResponseCode.DUPLICATE_DATA, message);
    }
}
