-- 서울대 2026학년도 학부 신입생의 학기당 수업료를 프로젝트 학과에 대응한다.
-- 1학기: https://cst.snu.ac.kr/uploads/attachments/attachment-notice-079-05-0601a7959e.pdf
-- 2학기: https://www.snu.ac.kr/webdata/uploads/kor/file/2026/08/2026_tuition.pdf
-- lms_academic의 실제 학과 이름과 학기를 확인한 환경에서 실행한다.
-- 기존 기준 금액과 기존 고지는 덮어쓰지 않는다. 다른 연도는 삽입하지 않는다.
INSERT INTO department_tuition_rates (department_id, semester_id, amount, source_url)
SELECT d.id, s.id, r.amount,
       CASE s.term
           WHEN 'FIRST' THEN 'https://cst.snu.ac.kr/uploads/attachments/attachment-notice-079-05-0601a7959e.pdf'
           ELSE 'https://www.snu.ac.kr/webdata/uploads/kor/file/2026/08/2026_tuition.pdf'
       END
FROM lms_academic.departments d
JOIN (
    SELECT '컴퓨터공학과' AS name, 2998000 AS amount
    UNION ALL SELECT '전자공학과', 2998000
    UNION ALL SELECT '기계공학과', 2998000
    UNION ALL SELECT '화학공학과', 2998000
    UNION ALL SELECT '국어국문학과', 2442000
    UNION ALL SELECT '영어영문학과', 2442000
    UNION ALL SELECT '사학과', 2442000
    UNION ALL SELECT '경영학과', 2442000
    UNION ALL SELECT '회계학과', 2442000
    UNION ALL SELECT '국제통상학과', 2442000
    UNION ALL SELECT '수학과', 2450000
    UNION ALL SELECT '물리학과', 2975000
    UNION ALL SELECT '화학과', 2975000
    UNION ALL SELECT '심리학과', 2679000
    UNION ALL SELECT '사회복지학과', 2442000
    UNION ALL SELECT '정치외교학과', 2442000
    UNION ALL SELECT '미술학과', 3653000
    UNION ALL SELECT '교육학과', 2442000
) r ON r.name = d.name
JOIN lms_academic.semesters s ON s.academic_year = 2026 AND s.term IN ('FIRST', 'SECOND')
WHERE d.active = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM department_tuition_rates existing
      WHERE existing.department_id = d.id AND existing.semester_id = s.id
  );
