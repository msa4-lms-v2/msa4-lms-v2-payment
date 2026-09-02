package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class InstallmentPlanAlreadyReviewedException extends BusinessException {

    public InstallmentPlanAlreadyReviewedException(String message) {
        super(CustomResponseCode.DUPLICATE_DATA, message);
    }
}
