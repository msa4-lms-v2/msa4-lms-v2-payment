package com.msa4lmsv2payment.domain.dashboard;

import java.util.List;
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
        if (semester == null) {
            return new AdminDashboardResponse(null, new AdminDashboardResponse.Summary(0, 0),
                    List.of(), null);
        }
        return new AdminDashboardResponse(semester, queries.summary(semester.id()),
                queries.tasks(semester.id()), queries.tuitionStats(semester.id()));
    }
}
