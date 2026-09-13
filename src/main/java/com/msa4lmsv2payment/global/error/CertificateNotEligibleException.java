package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

/**
 * 재학 상태가 아니거나, 졸업요건을 충족하지 못했거나, 재직 상태가 아니어서 증명서를 발급할 수 없을 때.
 */
public class CertificateNotEligibleException extends BusinessException {

    public CertificateNotEligibleException(String message) {
        super(CustomResponseCode.INVALID_PARAMETER, message);
    }
}
