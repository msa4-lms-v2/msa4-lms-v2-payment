package com.msa4lmsv2payment.global.client;

public record StudentSnapshotSyncResponse(
        Long studentId, Long userId, String displayName, String departmentName, Long sourceVersion) {
}
