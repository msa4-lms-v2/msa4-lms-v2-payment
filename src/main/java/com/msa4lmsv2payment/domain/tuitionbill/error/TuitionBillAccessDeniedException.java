package com.msa4lmsv2payment.domain.tuitionbill.error;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class TuitionBillAccessDeniedException extends BusinessException {

    public TuitionBillAccessDeniedException(String message) {
        super(CustomResponseCode.ACCESS_DENIED, message);
    }
}
