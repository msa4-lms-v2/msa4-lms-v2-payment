package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class AcademicServiceUnavailableException extends BusinessException {

    public AcademicServiceUnavailableException(String message) {
        super(CustomResponseCode.DEPENDENCY_UNAVAILABLE, message);
    }
}
