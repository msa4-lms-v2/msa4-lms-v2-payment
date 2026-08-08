package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.error.NotStudentAccountException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

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
}
