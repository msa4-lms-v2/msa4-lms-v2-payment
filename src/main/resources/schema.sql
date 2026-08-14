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
    withdrawal_id       BIGINT COMMENT 'Academic withdrawal_requests.id, FK 아님. WITHDRAWAL 환불에만 사용',
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

-- 2026-08-08: week-3 착수분 (payments, documents) 추가 — MY-PLAN_payment.md 10절
-- documents.document_type에 PAYMENT_CERTIFICATE(납부 확인서)를 포함한다(7-8절, docs-v2/MSA-LMS_ERD.md 5-3절 갱신).

CREATE TABLE IF NOT EXISTS payments (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tuition_bill_id    BIGINT NOT NULL,
    student_id         BIGINT NOT NULL COMMENT 'Academic.students.id 참조, FK 아님',
    amount             DECIMAL(12, 0) NOT NULL,
    method             VARCHAR(20) NOT NULL COMMENT 'CARD, VIRTUAL_ACCOUNT, TRANSFER',
    pg_transaction_id  VARCHAR(100) COMMENT '토스 paymentKey - confirm 성공 후 채워짐',
    status             VARCHAR(20) NOT NULL COMMENT 'REQUESTED, SUCCEEDED, FAILED, CANCELLED',
    requested_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at       DATETIME,
    UNIQUE KEY uk_payments_pg_transaction_id (pg_transaction_id),
    INDEX idx_payments_tuition_bill_id (tuition_bill_id),
    CONSTRAINT fk_payments_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- refunds.payment_id는 week-2 시점엔 payments가 없어 FK 없이 컬럼만 있었다. 이제 생겼으니 FK를 추가한다(B20번 - 기존 CREATE TABLE 문은 손대지 않고 ALTER로만 반영).
ALTER TABLE refunds
    ADD CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id);

CREATE TABLE IF NOT EXISTS documents (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id          BIGINT COMMENT 'Academic.users.id 참조, FK 아님',
    professor_id        BIGINT COMMENT 'Academic.users.id 참조, FK 아님',
    document_type       VARCHAR(30) NOT NULL COMMENT 'ENROLLMENT, GRADUATION, GRADE, EMPLOYMENT, PAYMENT_CERTIFICATE',
    issued_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_path           VARCHAR(500),
    verification_token  VARCHAR(100) NOT NULL,
    qr_hash             VARCHAR(100),
    revoked_at          DATETIME,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_documents_verification_token (verification_token),
    INDEX idx_documents_student_id (student_id),
    CONSTRAINT chk_documents_owner_exclusive CHECK (
        (student_id IS NOT NULL AND professor_id IS NULL) OR (student_id IS NULL AND professor_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SCRUM-177(실패한 환불 재시도, 비기능 #26) - 재시도 횟수를 남겨 "최종 실패" 상태를 판단할 근거로 쓴다.
ALTER TABLE refunds ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

-- 2026-08-10: ERD 리뷰 반영 - applyWithdrawalRefundRate()의 "있으면 갱신, 없으면 생성" 패턴이
-- DB 제약 없이 앱 로직(findByTuitionBillIdAndRefundType)만으로 중복을 막고 있어 동시요청 경쟁조건에 노출돼 있었다.
-- 같은 (tuition_bill_id, refund_type) 조합의 두 번째 INSERT를 DB가 직접 거부하게 한다.
ALTER TABLE refunds ADD CONSTRAINT uk_refunds_tuition_bill_type UNIQUE (tuition_bill_id, refund_type);

-- refund_rate는 0~1 사이 비율인데 계산 로직 버그로 음수·1 초과값이 저장될 여지를 DB 레벨에서 막는다.
ALTER TABLE refunds ADD CONSTRAINT chk_refunds_rate CHECK (refund_rate BETWEEN 0 AND 1);

-- 2026-08-15: 분할납부(installment) - 등록금 고지 1건을 회차별로 나눠 결제할 수 있게 계획을 저장한다.
-- payments.installment_plan_item_id로 어느 회차의 결제인지 연결하고, 회차 금액은 항상 서버가 계산해 위조를 막는다(기존 payment-amount-validation과 동일 원칙).
-- 신청만으로는 분할납부를 시작할 수 없다 - ADMIN이 승인(REQUESTED -> ACTIVE)해야 회차 결제가 가능하다.
CREATE TABLE IF NOT EXISTS installment_plans (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tuition_bill_id BIGINT NOT NULL,
    total_rounds    INT NOT NULL,
    status          VARCHAR(20) NOT NULL COMMENT 'REQUESTED, ACTIVE, REJECTED, COMPLETED',
    reviewed_by     BIGINT COMMENT 'Academic.users.id 참조, FK 아님',
    reviewed_at     DATETIME,
    reject_reason   VARCHAR(255),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_installment_plans_tuition_bill_id (tuition_bill_id),
    CONSTRAINT fk_installment_plans_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS installment_plan_items (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    installment_plan_id  BIGINT NOT NULL,
    round_no             INT NOT NULL,
    amount               DECIMAL(12, 0) NOT NULL,
    due_date             DATE NOT NULL,
    payment_id           BIGINT COMMENT '이 회차를 결제한 payments.id, 결제 전에는 NULL',
    status               VARCHAR(20) NOT NULL COMMENT 'SCHEDULED, PAID',
    UNIQUE KEY uk_installment_plan_items_plan_round (installment_plan_id, round_no),
    CONSTRAINT fk_installment_plan_items_plan FOREIGN KEY (installment_plan_id) REFERENCES installment_plans (id),
    CONSTRAINT fk_installment_plan_items_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ERD 리뷰(2026-08-10)는 결제 1건이 서로 다른 두 회차의 완납 처리에 쓰이지 않도록 UNIQUE를 권고했지만,
-- 이 컬럼에 그대로 걸면 결제 실패·방치 후 같은 회차를 다시 결제하려는 정상 재시도까지 DB 제약 위반으로 막힌다
-- (일반 전액결제도 같은 이유로 tuition_bill_id에 UNIQUE를 걸지 않는다). 대신 InstallmentPlanService.getItemOrThrow가
-- 이미 PAID인 회차의 신규 체크아웃 세션 생성을 막고, 기존 TuitionOverpaymentGuard가 합계 기준 이중 청구를 막는다.
ALTER TABLE payments ADD COLUMN installment_plan_item_id BIGINT COMMENT '분할납부 회차 결제일 때만 채워짐, installment_plan_items.id 참조';
ALTER TABLE payments ADD CONSTRAINT fk_payments_installment_plan_item FOREIGN KEY (installment_plan_item_id) REFERENCES installment_plan_items (id);

-- 2026-08-15: 장학금 신청(student-initiated) - 기존 scholarships/scholarship-discounts는 관리자가 배분을 확정하는 API만 있어,
-- 학생이 직접 신청을 접수하는 절차와 그 승인 이력을 별도로 남긴다. 승인되면 이 신청을 근거로 scholarships 행이 생성된다.
CREATE TABLE IF NOT EXISTS scholarship_application_periods (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    semester_id          BIGINT NOT NULL COMMENT 'Academic.semesters.id 참조, FK 아님',
    start_date           DATE NOT NULL,
    end_date             DATE NOT NULL,
    academic_schedule_id BIGINT COMMENT 'Academic.academic_schedules.id 참조, FK 아님. 학사일정 공지와 연결할 때만 채움(선택)',
    created_by           BIGINT NOT NULL COMMENT 'Academic.users.id 참조, FK 아님',
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scholarship_application_periods_semester_id (semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scholarship_applications (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    tuition_bill_id  BIGINT NOT NULL,
    student_id       BIGINT NOT NULL COMMENT 'Academic.students.id 참조, FK 아님',
    type             VARCHAR(20) NOT NULL COMMENT 'MERIT, NEED_BASED, OTHER (scholarships.type과 동일 체계)',
    requested_amount DECIMAL(12, 0) NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    status           VARCHAR(20) NOT NULL COMMENT 'REQUESTED, APPROVED, REJECTED',
    reviewed_by      BIGINT COMMENT 'Academic.users.id 참조, FK 아님',
    reviewed_at      DATETIME,
    reject_reason    VARCHAR(255),
    scholarship_id   BIGINT COMMENT '승인 시 생성된 scholarships.id',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scholarship_applications_tuition_bill_id (tuition_bill_id),
    INDEX idx_scholarship_applications_student_id (student_id),
    CONSTRAINT fk_scholarship_applications_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id),
    CONSTRAINT fk_scholarship_applications_scholarship FOREIGN KEY (scholarship_id) REFERENCES scholarships (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
