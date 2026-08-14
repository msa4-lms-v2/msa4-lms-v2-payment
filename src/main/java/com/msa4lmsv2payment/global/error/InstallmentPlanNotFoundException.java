package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class InstallmentPlanNotFoundException extends BusinessException {

    public InstallmentPlanNotFoundException(String message) {
        super(CustomResponseCode.NOT_FOUND_DATA, message);
    }
}
