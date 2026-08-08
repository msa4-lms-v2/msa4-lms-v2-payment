package com.msa4lmsv2payment.global.response;

public record GlobalRes<T>(String code, String message, T data) {

    public static <T> GlobalRes<T> success(T data) {
        return new GlobalRes<>(CustomResponseCode.SUCCESS.getCode(), "정상 처리되었습니다.", data);
    }

    public static GlobalRes<Void> success() {
        return new GlobalRes<>(CustomResponseCode.SUCCESS.getCode(), "정상 처리되었습니다.", null);
    }

    public static <T> GlobalRes<T> fail(CustomResponseCode c, String message, T data) {
        return new GlobalRes<>(c.getCode(), message, data);
    }
}
