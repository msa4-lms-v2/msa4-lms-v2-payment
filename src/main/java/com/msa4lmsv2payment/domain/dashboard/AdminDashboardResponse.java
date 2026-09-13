package com.msa4lmsv2payment.domain.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        CurrentSemester currentSemester, Summary summary, List<Task> tasks, TuitionStats tuitionStats) {
    public record CurrentSemester(Long id, int year, String label) {}
    public record Summary(long installmentPending, long scholarshipPending) {}
    public record Task(long id, String type, String requesterName, LocalDateTime requestedAt, Long tuitionBillId) {}
    public record TuitionStats(long paid, long inProgress, long unpaid) {}
}
