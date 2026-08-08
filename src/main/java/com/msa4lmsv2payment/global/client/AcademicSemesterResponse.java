package com.msa4lmsv2payment.global.client;

import java.time.LocalDate;

public record AcademicSemesterResponse(Long id, String term, Boolean isCurrent, LocalDate startDate, LocalDate endDate) {
}
