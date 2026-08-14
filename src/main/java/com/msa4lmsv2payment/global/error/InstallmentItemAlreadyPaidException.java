package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;

public class InstallmentItemAlreadyPaidException extends BusinessException {

    public InstallmentItemAlreadyPaidException(String message) {
        super(CustomResponseCode.DUPLICATE_DATA, message);
    }
}
