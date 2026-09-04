package com.msa4lmsv2payment.global.client;

import java.time.LocalDate;

public record SemesterSnapshotSyncResponse(
        Long semesterId, String displayName, LocalDate startDate, LocalDate endDate, Long sourceVersion) {
}
