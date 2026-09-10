package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;

public class FileStorageException extends BusinessException {

    public FileStorageException(String message) {
        super(CustomResponseCode.SYSTEM_ERROR, message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(CustomResponseCode.SYSTEM_ERROR, message);
        initCause(cause);
    }
}
