package com.msa4lmsv2payment.global.client;

import java.time.LocalDate;

public record WithdrawalSnapshotSyncResponse(
        Long withdrawalId, Long studentId, LocalDate effectiveDate, Long sourceVersion) {
}
