package com.msa4lmsv2payment.global.client;

import java.time.LocalDateTime;

public record AcademicWithdrawalHistoryResponse(String previousStatus, String newStatus, LocalDateTime processedAt) {
}
