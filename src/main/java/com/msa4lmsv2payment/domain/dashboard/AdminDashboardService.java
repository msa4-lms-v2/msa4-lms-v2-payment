package com.msa4lmsv2payment.domain.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final CurrentSemesterClient currentSemesterClient;
    private final AdminDashboardQueryRepository queries;

    // Do not hold a database transaction/connection during the Academic HTTP request.
    public AdminDashboardResponse getDashboard() {
        var semester = currentSemesterClient.getCurrentSemester();
        return new AdminDashboardResponse(semester, queries.summary(), queries.tasks(),
                semester == null ? null : queries.tuitionStats(semester.id()));
    }
}
