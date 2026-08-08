package com.msa4lmsv2payment.domain.virtualaccount.error;

import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class VirtualAccountNotFoundException extends BusinessException {

    public VirtualAccountNotFoundException(String message) {
        super(CustomResponseCode.NOT_FOUND_DATA, message);
    }
}
