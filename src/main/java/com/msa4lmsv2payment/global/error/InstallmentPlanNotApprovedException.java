package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class InstallmentPlanNotApprovedException extends BusinessException {

    public InstallmentPlanNotApprovedException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
