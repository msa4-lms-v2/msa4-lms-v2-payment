package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

/**
 * 아직 납부 이력이 없는 고지에 납부 확인서를 발급하려 할 때.
 */
public class PaymentNotCompletedException extends BusinessException {

    public PaymentNotCompletedException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
