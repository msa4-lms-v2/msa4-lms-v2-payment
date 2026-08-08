package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.error.NotStudentAccountException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Academic이 없어도 로컬 개발·테스트·발표 백업으로 단독 기동하기 위한 고정값 스텁 - MY-PLAN_payment.md 4-1절.
 * userId와 studentId를 일부러 다른 값으로 매핑한다 - 두 ID를 항등(studentId=userId)으로 두면 혼동 버그가
 * 로컬에서 절대 드러나지 않고 Academic이 실제로 붙는 순간 터진다.
 */
@Slf4j
@Profile("stub")
@Component
public class AcademicStubClient implements AcademicClient {

    private static final Map<Long, Long> USER_ID_TO_STUDENT_ID = Map.of(
            1L, 20260001L,
            2L, 20260002L
    );
    private static final Set<Long> KNOWN_STUDENT_IDS = Set.of(20260001L, 20260002L);
    // 7-3절 기준값(2026-2학기, 개강 9/1·종강 12/18)
    private static final LocalDate SEMESTER_START_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEMESTER_END_DATE = LocalDate.of(2026, 12, 18);

    /**
     * 환불률 구간 4개(5/6, 2/3, 1/2, 0%)를 값만 바꿔 시연하기 위한 설정값 - MY-PLAN_payment.md 4-1절 스텁 스펙.
     * 기본값(발표일 2026-09-18)은 7-3절 예시대로 5/6 구간에 해당한다.
     */
    @Value("${academic.stub.withdrawal-processed-at:2026-09-18T10:00:00}")
    private LocalDateTime withdrawalProcessedAt;

    @PostConstruct
    public void warnStubActive() {
        log.warn("### AcademicStubClient 활성화 - 고정값(studentId 매핑 {}) 사용 중, Academic 실제 응답이 아님 ###",
                USER_ID_TO_STUDENT_ID);
    }

    @Override
    public AcademicStudentResponse findStudentByUserId(Long userId) {
        Long studentId = USER_ID_TO_STUDENT_ID.get(userId);
        if (studentId == null) {
            throw new NotStudentAccountException("학생 계정이 아니거나 존재하지 않는 사용자입니다.");
        }
        return new AcademicStudentResponse(studentId, userId, "ENROLLED");
    }

    @Override
    public AcademicStudentExistsResponse findStudent(Long studentId) {
        if (!KNOWN_STUDENT_IDS.contains(studentId)) {
            throw new AcademicResourceNotFoundException("존재하지 않는 학번입니다: " + studentId);
        }
        return new AcademicStudentExistsResponse(studentId, "ENROLLED");
    }

    @Override
    public AcademicSemesterResponse findSemester(Long semesterId) {
        return new AcademicSemesterResponse(semesterId, "SECOND", true, SEMESTER_START_DATE, SEMESTER_END_DATE);
    }

    @Override
    public AcademicWithdrawalHistoryResponse findLatestWithdrawalHistory(Long studentId) {
        if (!KNOWN_STUDENT_IDS.contains(studentId)) {
            throw new AcademicResourceNotFoundException("자퇴 처리 이력을 찾을 수 없습니다: studentId=" + studentId);
        }
        return new AcademicWithdrawalHistoryResponse("ENROLLED", "ON_LEAVE", withdrawalProcessedAt);
    }
}
