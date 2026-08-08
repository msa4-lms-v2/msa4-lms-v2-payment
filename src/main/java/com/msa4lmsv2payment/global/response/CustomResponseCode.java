package com.msa4lmsv2payment.global.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CustomResponseCode {

    SUCCESS("00", HttpStatus.OK),

    UNAUTHENTICATED("E02", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("E03", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("E04", HttpStatus.UNAUTHORIZED),

    NOT_FOUND_DATA("E10", HttpStatus.NOT_FOUND),

    INVALID_PARAMETER("E21", HttpStatus.BAD_REQUEST),

    ACADEMIC_SERVICE_UNAVAILABLE("E89", HttpStatus.SERVICE_UNAVAILABLE),
    TOSS_SERVICE_UNAVAILABLE("E88", HttpStatus.SERVICE_UNAVAILABLE),
    SYSTEM_ERROR("E99", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;

    CustomResponseCode(String code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
