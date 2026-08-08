package com.msa4lmsv2payment.domain.scholarship.error;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class ScholarshipExceedsBillingAmountException extends BusinessException {

    public ScholarshipExceedsBillingAmountException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
