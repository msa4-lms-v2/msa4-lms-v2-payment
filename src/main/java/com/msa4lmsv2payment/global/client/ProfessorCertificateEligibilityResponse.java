package com.msa4lmsv2payment.global.client;

public record ProfessorCertificateEligibilityResponse(
        Long professorId,
        String professorNumber,
        String name,
        String departmentName,
        String collegeName,
        Short hireYear,
        String status
) {
}
