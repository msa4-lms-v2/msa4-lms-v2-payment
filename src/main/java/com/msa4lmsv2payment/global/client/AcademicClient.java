package com.msa4lmsv2payment.global.client;

/**
 * Payment가 Academic에 요청하는 조회 계약. 구현체는 AcademicHttpClient(실제 호출) 하나다.
 */
public interface AcademicClient {

    /**
     * JWT 사용자 ID(users.id)로 본인의 학번(students.id)을 조회한다.
     */
    AcademicStudentResponse findStudentByUserId(Long userId);

    /**
     * 등록금 고지 생성 전 학번(students.id)이 실제로 존재하는지 확인한다.
     */
    AcademicStudentExistsResponse findStudent(Long studentId);

    /**
     * 등록금 고지 생성 전 학기가 실제로 존재하는지 확인한다. 개강일·종강일은 자퇴 환불률 산정에도 재사용된다.
     */
    AcademicSemesterResponse findSemester(Long semesterId);

    /**
     * 자퇴 환불률 산정을 위해 특정 자퇴 신청 건(승인 상태, 효력일)을 조회한다.
     */
    AcademicWithdrawalResponse findWithdrawal(Long withdrawalId);
}
