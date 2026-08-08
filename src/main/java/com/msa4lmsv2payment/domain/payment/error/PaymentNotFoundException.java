package com.msa4lmsv2payment.domain.payment.error;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class PaymentNotFoundException extends BusinessException {

    public PaymentNotFoundException(String message) {
        super(CustomResponseCode.NOT_FOUND_DATA, message);
    }
}
