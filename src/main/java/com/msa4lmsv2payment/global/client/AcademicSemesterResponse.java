package com.msa4lmsv2payment.global.client;

import java.time.LocalDate;

public record AcademicSemesterResponse(Long id, LocalDate startDate, LocalDate endDate) {
}
