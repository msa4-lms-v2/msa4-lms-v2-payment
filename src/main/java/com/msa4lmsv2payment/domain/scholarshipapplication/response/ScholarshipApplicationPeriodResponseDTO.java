package com.msa4lmsv2payment.domain.scholarshipapplication.response;

import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record ScholarshipApplicationPeriodResponseDTO(
        @Schema(description = "신청기간 ID") Long id,
        @Schema(description = "학기 ID") Long semesterId,
        @Schema(description = "신청 시작일") LocalDate startDate,
        @Schema(description = "신청 종료일") LocalDate endDate,
        @Schema(description = "연결된 Academic 학사일정 ID") Long academicScheduleId,
        @Schema(description = "오늘 기준 신청 가능 여부") boolean open
) {
    public static ScholarshipApplicationPeriodResponseDTO from(ScholarshipApplicationPeriod period) {
        return new ScholarshipApplicationPeriodResponseDTO(
                period.getId(), period.getSemesterId(), period.getStartDate(), period.getEndDate(),
                period.getAcademicScheduleId(), period.isOpenOn(LocalDate.now()));
    }
}
