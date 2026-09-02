package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class PaymentResultMismatchException extends BusinessException {

    public PaymentResultMismatchException(String message) {
        super(CustomResponseCode.DUPLICATE_DATA, message);
    }
}
