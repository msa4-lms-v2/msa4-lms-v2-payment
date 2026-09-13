package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class InvalidAttachmentException extends BusinessException {

    public InvalidAttachmentException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
