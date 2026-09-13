package com.msa4lmsv2payment.global.client;

public record StudentCertificateEligibilityResponse(
        Long studentId,
        String studentNumber,
        String name,
        String departmentName,
        String collegeName,
        byte gradeLevel,
        short admissionYear,
        String academicStatus,
        Boolean graduationSatisfied,
        Integer earnedTotalCredits
) {
}
