package com.msa4lmsv2payment.global.client;

/**
 * docs-v2/MSA-LMS_INTEGRATION.md 기준 Payment -> Academic 호출 계약.
 * 구현체는 AcademicHttpClient(실제 호출)와 AcademicStubClient(고정값, "stub" 프로파일) 둘이다 - MY-PLAN_payment.md 4-1절.
 */
public interface AcademicClient {

    /**
     * 7절 - JWT 사용자 ID(users.id)로 본인의 학번(students.id)을 조회한다.
     */
    AcademicStudentResponse findStudentByUserId(Long userId);
}
