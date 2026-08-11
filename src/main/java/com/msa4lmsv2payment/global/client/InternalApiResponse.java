package com.msa4lmsv2payment.global.client;

public record InternalApiResponse<T>(String code, String message, T data) {

    public boolean isSuccess() {
        return "00".equals(code);
    }
}
