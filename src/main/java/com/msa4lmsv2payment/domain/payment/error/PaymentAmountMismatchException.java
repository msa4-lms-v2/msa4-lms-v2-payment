package com.msa4lmsv2payment.domain.payment.error;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.CustomResponseCode;

/**
 * SCRUM-51 - 요청 금액이 서버 계산 실납부액과 다를 때. 위조 요청 거부 목적이라 E21(검증)로 분류한다.
 */
public class PaymentAmountMismatchException extends BusinessException {

    public PaymentAmountMismatchException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
