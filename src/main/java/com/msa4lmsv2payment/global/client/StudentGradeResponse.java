package com.msa4lmsv2payment.global.client;

import java.math.BigDecimal;
import java.util.List;

// Academic의 본인 성적 조회 계약. 요약과 재수강 반영 여부는 Academic의 계산을 그대로 사용한다.
public record StudentGradeResponse(BigDecimal totalGpa, int queryCredits, List<Grade> grades) {
    public record Grade(short academicYear, String term, String courseCode, String courseName,
                        byte credits, String letterGrade, BigDecimal gradePoint, boolean reflectedInGpa) {
    }
}
