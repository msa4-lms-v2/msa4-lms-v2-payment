-- msa4-lms-v2-payment 스키마
-- 관리 방식: code_convention.md B20번(DB 마이그레이션) - 소규모/초기 단계 schema.sql 손 관리
-- 반영 이력은 analytics/report/msa4-lms-v2-payment/msa4-lms-v2-payment_report.md 에 기록한다.
-- 근거: docs-v2/MSA-LMS_ERD.md 4절(Payment·문서 서비스 ERD), 5-3절(상태값 정의)

-- 2026-08-08: week-1 착수분 (tuition_bills, scholarships) 최초 생성

CREATE TABLE IF NOT EXISTS tuition_bills (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id      BIGINT NOT NULL COMMENT 'Academic.students.id 참조, FK 아님',
    semester_id     BIGINT NOT NULL COMMENT 'Academic.semesters.id 참조, FK 아님',
    billing_amount  DECIMAL(12, 0) NOT NULL,
    due_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL COMMENT 'UNPAID, PARTIAL, PAID, OVERDUE',
    created_by      BIGINT NOT NULL COMMENT 'Academic.users.id 참조, FK 아님',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tuition_bills_student_id (student_id),
    INDEX idx_tuition_bills_semester_id (semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scholarships (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tuition_bill_id BIGINT NOT NULL,
    type            VARCHAR(20) NOT NULL COMMENT 'MERIT, NEED_BASED, OTHER',
    amount          DECIMAL(12, 0) NOT NULL,
    reason          VARCHAR(255),
    approved_by     BIGINT NOT NULL COMMENT 'Academic.users.id 참조, FK 아님',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scholarships_tuition_bill_id (tuition_bill_id),
    CONSTRAINT fk_scholarships_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
