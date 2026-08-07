package com.msa4lmsv2payment.domain.paymenthealth.response;

import java.time.LocalDateTime;

public record HealthCheckResponseDTO(
        String feature,
        String status,
        String message,
        LocalDateTime checkedAt
) {
    public static HealthCheckResponseDTO up(String feature, String message) {
        return new HealthCheckResponseDTO(feature, "UP", message, LocalDateTime.now());
    }

    public static HealthCheckResponseDTO down(String feature, String message) {
        return new HealthCheckResponseDTO(feature, "DOWN", message, LocalDateTime.now());
    }
}
