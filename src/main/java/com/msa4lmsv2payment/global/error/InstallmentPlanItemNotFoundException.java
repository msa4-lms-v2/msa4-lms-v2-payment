package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class InstallmentPlanItemNotFoundException extends BusinessException {

    public InstallmentPlanItemNotFoundException(String message) {
        super(CustomResponseCode.NOT_FOUND_DATA, message);
    }
}
