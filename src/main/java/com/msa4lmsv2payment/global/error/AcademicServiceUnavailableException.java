package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class AcademicServiceUnavailableException extends BusinessException {

    public AcademicServiceUnavailableException(String message) {
        super(CustomResponseCode.ACADEMIC_SERVICE_UNAVAILABLE, message);
    }
}
