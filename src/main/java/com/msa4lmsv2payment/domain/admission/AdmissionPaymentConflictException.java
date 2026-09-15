package com.msa4lmsv2payment.domain.admission;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class AdmissionPaymentConflictException extends BusinessException {
    public AdmissionPaymentConflictException(String message) {
        super(CustomResponseCode.DUPLICATE_DATA, message);
    }
}
