package com.msa4lmsv2payment.global.client;
import java.time.LocalDate;
import java.util.List;
public record ProfessorCareerResponse(Long userId, ProfessorCertificateEligibilityResponse professor,
                                      List<Teaching> lectures) {
    public record Teaching(Long classId, short academicYear, String term, String courseCode, String courseName,
                           String sectionNo, byte credits, LocalDate startDate, LocalDate endDate) {}
}
