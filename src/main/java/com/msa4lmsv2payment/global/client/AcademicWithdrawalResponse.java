package com.msa4lmsv2payment.global.client;

import java.time.LocalDate;

public record AcademicWithdrawalResponse(Long id, Long studentId, String status, LocalDate effectiveDate) {
}
