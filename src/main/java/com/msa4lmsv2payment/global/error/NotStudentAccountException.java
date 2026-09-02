package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class NotStudentAccountException extends BusinessException {

    public NotStudentAccountException(String message) {
        super(CustomResponseCode.ACCESS_DENIED, message);
    }
}
