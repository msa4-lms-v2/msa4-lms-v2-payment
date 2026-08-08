package com.msa4lmsv2payment.domain.tuitionbill.error;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class TuitionBillNotFoundException extends BusinessException {

    public TuitionBillNotFoundException(String message) {
        super(CustomResponseCode.NOT_FOUND_DATA, message);
    }
}
