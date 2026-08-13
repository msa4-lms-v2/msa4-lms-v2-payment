package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

/**
 * 확정 시점에 성공 결제 합계 + 신규 결제 금액이 현재 순납부액을 초과할 때.
 */
public class TuitionOverpaymentException extends BusinessException {

    public TuitionOverpaymentException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
