package com.msa4lmsv2payment.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 응답 포맷")
public record GlobalRes<T>(
        @Schema(description = "응답 코드(\"00\"=성공, 나머지는 E-코드 대역)", example = "00") String code,
        @Schema(description = "응답 메시지") String message,
        @Schema(description = "실제 응답 데이터") T data
) {

    public static <T> GlobalRes<T> success(T data) {
        return new GlobalRes<>(CustomResponseCode.SUCCESS.getCode(), CustomResponseCode.SUCCESS.name(), data);
    }

    public static GlobalRes<Void> success() {
        return new GlobalRes<>(CustomResponseCode.SUCCESS.getCode(), CustomResponseCode.SUCCESS.name(), null);
    }

    public static <T> GlobalRes<T> fail(CustomResponseCode c, T data) {
        return new GlobalRes<>(c.getCode(), c.name(), data);
    }
}
