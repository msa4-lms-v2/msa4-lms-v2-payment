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

-- 2026-08-08: week-2 착수분 (refunds, idempotency_keys, audit_logs, virtual_accounts) 추가
-- MY-PLAN_payment.md 7-4절 결정으로 virtual_accounts(가상계좌 발급)를 week-4에서 week-2로 당겼다.

CREATE TABLE IF NOT EXISTS virtual_accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tuition_bill_id BIGINT NOT NULL,
    account_number  VARCHAR(30) NOT NULL,
    bank_code       VARCHAR(10) NOT NULL,
    expires_at      DATETIME NOT NULL,
    status          VARCHAR(20) NOT NULL COMMENT 'ISSUED, DEPOSITED, EXPIRED',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_virtual_accounts_account_number (account_number),
    INDEX idx_virtual_accounts_tuition_bill_id (tuition_bill_id),
    CONSTRAINT fk_virtual_accounts_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refunds (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id          BIGINT COMMENT 'week-3에서 payments 테이블 생성 후 FK 추가 예정, 그전까지 FK 없음',
    virtual_account_id  BIGINT,
    tuition_bill_id     BIGINT NOT NULL,
    refund_type         VARCHAR(20) NOT NULL COMMENT 'WITHDRAWAL, PG_CANCEL, EXCESS_DEPOSIT',
    amount              DECIMAL(12, 0) NOT NULL,
    refund_rate         DECIMAL(5, 4) NOT NULL COMMENT '7-2절 반환율표 기준 (예: 0.8333 = 5/6)',
    status              VARCHAR(20) NOT NULL COMMENT 'REQUESTED, SUCCEEDED, FAILED, RETRYING',
    requested_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        DATETIME,
    INDEX idx_refunds_tuition_bill_id (tuition_bill_id),
    INDEX idx_refunds_virtual_account_id (virtual_account_id),
    CONSTRAINT fk_refunds_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id),
    CONSTRAINT fk_refunds_virtual_account FOREIGN KEY (virtual_account_id) REFERENCES virtual_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- idempotency_keys/audit_logs는 이력·임시 데이터 성격이라 소프트 삭제 대상이 아니다(ERD 1절 공통 규칙) - 물리 삭제 허용.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key       VARCHAR(100) NOT NULL,
    requester_student_id  BIGINT NOT NULL COMMENT 'Academic.students.id 참조, FK 아님',
    endpoint              VARCHAR(255) NOT NULL,
    request_hash          VARCHAR(64) NOT NULL,
    response_snapshot     JSON,
    status                VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS, COMPLETED',
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at            DATETIME NOT NULL,
    UNIQUE KEY uk_idempotency_keys_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id      BIGINT NOT NULL COMMENT 'Academic.users.id 참조, FK 아님',
    action        VARCHAR(50) NOT NULL COMMENT '7-6절 액션표 참고 (TUITION_BILL_CREATED, REFUND_REQUESTED 등)',
    target_type   VARCHAR(50) NOT NULL,
    target_id     BIGINT NOT NULL,
    before_value  JSON,
    after_value   JSON,
    reason        VARCHAR(255),
    request_id    VARCHAR(50),
    ip_address    VARCHAR(45),
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_logs_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
