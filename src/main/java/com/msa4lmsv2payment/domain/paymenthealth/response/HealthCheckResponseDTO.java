package com.msa4lmsv2payment.domain.paymenthealth.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record HealthCheckResponseDTO(
        @Schema(description = "점검 대상", example = "PG_SANDBOX") String feature,
        @Schema(description = "연결 상태", allowableValues = {"UP", "DOWN"}) String status,
        @Schema(description = "결과 메시지") String message,
        @Schema(description = "점검 시각") LocalDateTime checkedAt
) {
    public static HealthCheckResponseDTO up(String feature, String message) {
        return new HealthCheckResponseDTO(feature, "UP", message, LocalDateTime.now());
    }

    public static HealthCheckResponseDTO down(String feature, String message) {
        return new HealthCheckResponseDTO(feature, "DOWN", message, LocalDateTime.now());
    }
}
