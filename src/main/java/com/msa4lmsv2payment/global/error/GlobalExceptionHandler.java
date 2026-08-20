package com.msa4lmsv2payment.global.error;

import com.msa4lmsv2payment.global.response.CustomResponseCode;
import com.msa4lmsv2payment.global.response.GlobalRes;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GlobalRes<Void>> handleBusinessException(BusinessException e) {
        CustomResponseCode c = e.getCode();
        if (c.getHttpStatus().is5xxServerError()) {
            log.error("[{}] {}", c.getCode(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", c.getCode(), e.getMessage());
        }
        return ResponseEntity.status(c.getHttpStatus()).body(GlobalRes.fail(c, e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalRes<Void>> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("[{}] {}", CustomResponseCode.INVALID_PARAMETER.getCode(), message);
        return ResponseEntity.status(CustomResponseCode.INVALID_PARAMETER.getHttpStatus())
                .body(GlobalRes.fail(CustomResponseCode.INVALID_PARAMETER, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GlobalRes<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("[{}] {}", CustomResponseCode.INVALID_PARAMETER.getCode(), e.getMessage());
        return ResponseEntity.status(CustomResponseCode.INVALID_PARAMETER.getHttpStatus())
                .body(GlobalRes.fail(CustomResponseCode.INVALID_PARAMETER, null));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<GlobalRes<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        String message = "필수 헤더가 누락되었습니다: " + e.getHeaderName();
        log.warn("[{}] {}", CustomResponseCode.INVALID_PARAMETER.getCode(), message);
        return ResponseEntity.status(CustomResponseCode.INVALID_PARAMETER.getHttpStatus())
                .body(GlobalRes.fail(CustomResponseCode.INVALID_PARAMETER, null));
    }

    // @PreAuthorize 거부(AuthorizationDeniedException)는 컨트롤러 메서드 내부(AOP)에서 던져져
    // ExceptionTranslationFilter보다 먼저 여기로 들어온다. 이 핸들러가 없으면 catch-all(E99/500)로 샌다.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GlobalRes<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("[{}] {}", CustomResponseCode.ACCESS_DENIED.getCode(), e.getMessage());
        return ResponseEntity.status(CustomResponseCode.ACCESS_DENIED.getHttpStatus())
                .body(GlobalRes.fail(CustomResponseCode.ACCESS_DENIED, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalRes<Void>> handleException(Exception e) {
        log.error("[{}] {}", CustomResponseCode.SYSTEM_ERROR.getCode(), e.getMessage(), e);
        return ResponseEntity.status(CustomResponseCode.SYSTEM_ERROR.getHttpStatus())
                .body(GlobalRes.fail(CustomResponseCode.SYSTEM_ERROR, null));
    }
}
