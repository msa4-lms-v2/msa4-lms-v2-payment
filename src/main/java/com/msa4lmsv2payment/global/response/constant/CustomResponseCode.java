package com.msa4lmsv2payment.global.response.constant;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CustomResponseCode {

    SUCCESS("00", HttpStatus.OK, "정상 처리되었습니다."),

    UNAUTHENTICATED("E02", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED("E03", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_TOKEN("E04", HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),

    NOT_FOUND_DATA("E10", HttpStatus.NOT_FOUND, "요청한 데이터를 찾을 수 없습니다."),
    DUPLICATE_DATA("E11", HttpStatus.CONFLICT, "이미 처리되었거나 현재 상태와 충돌합니다."),

    INVALID_PARAMETER("E21", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    DEPENDENCY_UNAVAILABLE("E90", HttpStatus.SERVICE_UNAVAILABLE, "연동된 외부 서비스를 사용할 수 없습니다."),
    DEPENDENCY_TIMEOUT("E91", HttpStatus.GATEWAY_TIMEOUT, "연동된 외부 서비스 응답이 지연되고 있습니다."),
    CIRCUIT_OPEN("E92", HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    SERVICE_RECOVERING("E93", HttpStatus.SERVICE_UNAVAILABLE, "서비스가 복구 중입니다. 잠시 후 다시 시도해 주세요."),
    MANUAL_REVIEW_REQUIRED("E94", HttpStatus.CONFLICT, "수동 확인이 필요합니다."),
    SYSTEM_ERROR("E99", HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    CustomResponseCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
