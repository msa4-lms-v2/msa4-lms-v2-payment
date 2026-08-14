package com.msa4lmsv2payment.domain.scholarshipapplication.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ScholarshipApplicationPeriodCreateRequestDTO(
        @Schema(description = "학기 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "학기 ID는 필수입니다.") Long semesterId,
        @Schema(description = "신청 시작일", example = "2026-02-16", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "신청 시작일은 필수입니다.") LocalDate startDate,
        @Schema(description = "신청 종료일", example = "2026-02-27", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "신청 종료일은 필수입니다.") LocalDate endDate,
        @Schema(description = "연결할 Academic 학사일정 ID(선택, 학사일정 공지와 연결할 때만 지정)", example = "12")
        Long academicScheduleId
) {
}
