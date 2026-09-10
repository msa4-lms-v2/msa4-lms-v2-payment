-- msa4-lms-v2-payment 스키마
-- 소규모/초기 단계라 별도 마이그레이션 도구 없이 이 schema.sql을 직접 손으로 관리한다.
-- 반영 이력은 analytics/report/msa4-lms-v2-payment/msa4-lms-v2-payment_report.md 에 기록한다.

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
-- virtual_accounts(가상계좌 발급)는 원래 이후 단계 예정이었으나 이 단계로 앞당겼다.

CREATE TABLE IF NOT EXISTS virtual_accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tuition_bill_id BIGINT NOT NULL,
    order_id        VARCHAR(64) NOT NULL COMMENT '발급 시 토스에 보낸 orderId. 입금 Webhook이 이 값으로 계좌를 찾는다',
    secret          VARCHAR(64) NOT NULL COMMENT '토스 발급 응답의 virtualAccount.secret. 입금 Webhook 본문의 secret과 대조해 위조 요청을 막는다',
    account_number  VARCHAR(30) NOT NULL,
    bank_code       VARCHAR(10) NOT NULL,
    expires_at      DATETIME NOT NULL,
    status          VARCHAR(20) NOT NULL COMMENT 'ISSUED, PARTIALLY_DEPOSITED, DEPOSITED, EXPIRED',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_virtual_accounts_account_number (account_number),
    UNIQUE KEY uk_virtual_accounts_order_id (order_id),
    INDEX idx_virtual_accounts_tuition_bill_id (tuition_bill_id),
    CONSTRAINT fk_virtual_accounts_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4주차(입금 Webhook) 도입 전에 만들어진 실제 DB에는 order_id/secret 컬럼이 없어 재실행해도 안전하게 추가한다.
SET @virtual_accounts_order_id_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_accounts' AND COLUMN_NAME = 'order_id'
);
SET @virtual_accounts_order_id_ddl = IF(@virtual_accounts_order_id_exists = 0,
    'ALTER TABLE virtual_accounts ADD COLUMN order_id VARCHAR(64) NOT NULL DEFAULT '''', ADD CONSTRAINT uk_virtual_accounts_order_id UNIQUE (order_id)',
    'SELECT 1');
PREPARE virtual_accounts_order_id_stmt FROM @virtual_accounts_order_id_ddl;
EXECUTE virtual_accounts_order_id_stmt;
DEALLOCATE PREPARE virtual_accounts_order_id_stmt;

SET @virtual_accounts_secret_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_accounts' AND COLUMN_NAME = 'secret'
);
SET @virtual_accounts_secret_ddl = IF(@virtual_accounts_secret_exists = 0,
    'ALTER TABLE virtual_accounts ADD COLUMN secret VARCHAR(64) NOT NULL DEFAULT ''''',
    'SELECT 1');
PREPARE virtual_accounts_secret_stmt FROM @virtual_accounts_secret_ddl;
EXECUTE virtual_accounts_secret_stmt;
DEALLOCATE PREPARE virtual_accounts_secret_stmt;

-- 4주차: 분할납부 회차별 가상계좌 연결 - payments.installment_plan_item_id와 같은 이유로 회차 결제일 때만 채워진다.
-- FK는 installment_plan_items 테이블이 생긴 뒤(분할납부 섹션)에 추가한다.
SET @virtual_accounts_installment_plan_item_id_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_accounts' AND COLUMN_NAME = 'installment_plan_item_id'
);
SET @virtual_accounts_installment_plan_item_id_ddl = IF(@virtual_accounts_installment_plan_item_id_exists = 0,
    'ALTER TABLE virtual_accounts ADD COLUMN installment_plan_item_id BIGINT COMMENT ''분할납부 회차 결제일 때만 채워짐, installment_plan_items.id 참조''',
    'SELECT 1');
PREPARE virtual_accounts_installment_plan_item_id_stmt FROM @virtual_accounts_installment_plan_item_id_ddl;
EXECUTE virtual_accounts_installment_plan_item_id_stmt;
DEALLOCATE PREPARE virtual_accounts_installment_plan_item_id_stmt;

-- 4주차: 토스 가상계좌 발급 응답 최상위(Payment 객체)의 paymentKey를 저장한다 - 입금 완료 후 이 계좌를
-- 실제로 취소(환불)하려면 입금 시점의 거래키(toss_transaction_key)가 아니라 발급 시점의 paymentKey가 필요하다.
SET @virtual_accounts_payment_key_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_accounts' AND COLUMN_NAME = 'payment_key'
);
SET @virtual_accounts_payment_key_ddl = IF(@virtual_accounts_payment_key_exists = 0,
    'ALTER TABLE virtual_accounts ADD COLUMN payment_key VARCHAR(200) COMMENT ''토스 가상계좌 발급 응답의 paymentKey - 환불(취소) 호출에 사용''',
    'SELECT 1');
PREPARE virtual_accounts_payment_key_stmt FROM @virtual_accounts_payment_key_ddl;
EXECUTE virtual_accounts_payment_key_stmt;
DEALLOCATE PREPARE virtual_accounts_payment_key_stmt;

CREATE TABLE IF NOT EXISTS virtual_account_deposits (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    virtual_account_id  BIGINT NOT NULL,
    amount              DECIMAL(12, 0) NOT NULL,
    toss_transaction_key VARCHAR(200) NOT NULL COMMENT '동일 가상계좌 거래의 중복 반영 방지용 Toss 거래키',
    webhook_event_id    VARCHAR(200) NOT NULL COMMENT 'tosspayments-webhook-transmission-id, 동일 Webhook 재전송 방지용',
    received_at         DATETIME NOT NULL COMMENT '토스가 통보한 입금 시각',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_virtual_account_deposits_transaction_key (toss_transaction_key),
    UNIQUE KEY uk_virtual_account_deposits_event (webhook_event_id),
    INDEX idx_virtual_account_deposits_virtual_account_id (virtual_account_id),
    CONSTRAINT fk_virtual_account_deposits_virtual_account FOREIGN KEY (virtual_account_id) REFERENCES virtual_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기존 입금 테이블에는 Webhook 전송 ID가 없으므로 재실행 가능한 migration으로 추가한다.
SET @virtual_account_deposits_event_id_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_account_deposits' AND COLUMN_NAME = 'webhook_event_id'
);
SET @virtual_account_deposits_event_id_ddl = IF(@virtual_account_deposits_event_id_exists = 0,
    'ALTER TABLE virtual_account_deposits ADD COLUMN webhook_event_id VARCHAR(200), ADD CONSTRAINT uk_virtual_account_deposits_event UNIQUE (webhook_event_id)',
    'SELECT 1');
PREPARE virtual_account_deposits_event_id_stmt FROM @virtual_account_deposits_event_id_ddl;
EXECUTE virtual_account_deposits_event_id_stmt;
DEALLOCATE PREPARE virtual_account_deposits_event_id_stmt;

CREATE TABLE IF NOT EXISTS refunds (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id          BIGINT COMMENT 'week-3에서 payments 테이블 생성 후 FK 추가 예정, 그전까지 FK 없음',
    virtual_account_id  BIGINT,
    tuition_bill_id     BIGINT NOT NULL,
    withdrawal_id       BIGINT COMMENT 'Academic withdrawal_requests.id, FK 아님. WITHDRAWAL 환불에만 사용',
    refund_type         VARCHAR(20) NOT NULL COMMENT 'WITHDRAWAL, PG_CANCEL, EXCESS_DEPOSIT',
    amount              DECIMAL(12, 0) NOT NULL,
    refund_rate         DECIMAL(5, 4) NOT NULL COMMENT '자퇴 환불률 (예: 0.8333 = 5/6)',
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
    action        VARCHAR(50) NOT NULL COMMENT '업무 액션 코드 (TUITION_BILL_CREATED, REFUND_REQUESTED 등)',
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

-- 2026-08-08: week-3 착수분 (payments, documents) 추가
-- documents.document_type에 PAYMENT_CERTIFICATE(납부 확인서)를 포함한다.

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

-- refunds.payment_id는 week-2 시점엔 payments가 없어 FK 없이 컬럼만 있었다. 이제 생겼으니 FK를 추가한다.
-- 기존 CREATE TABLE 문은 손대지 않고 이렇게 ALTER로만 반영한다. 재실행해도 중복 추가되지 않도록 존재 여부를 먼저 확인한다.
SET @fk_refunds_payment_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND CONSTRAINT_NAME = 'fk_refunds_payment'
);
SET @fk_refunds_payment_ddl = IF(@fk_refunds_payment_exists = 0,
    'ALTER TABLE refunds ADD CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)',
    'SELECT 1');
PREPARE fk_refunds_payment_stmt FROM @fk_refunds_payment_ddl;
EXECUTE fk_refunds_payment_stmt;
DEALLOCATE PREPARE fk_refunds_payment_stmt;

CREATE TABLE IF NOT EXISTS documents (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id          BIGINT COMMENT 'Academic.students.id 참조, FK 아님',
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

-- 실패한 환불 재시도 - 재시도 횟수를 남겨 "최종 실패" 상태를 판단할 근거로 쓴다.
SET @refunds_retry_count_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'retry_count'
);
SET @refunds_retry_count_ddl = IF(@refunds_retry_count_exists = 0,
    'ALTER TABLE refunds ADD COLUMN retry_count INT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE refunds_retry_count_stmt FROM @refunds_retry_count_ddl;
EXECUTE refunds_retry_count_stmt;
DEALLOCATE PREPARE refunds_retry_count_stmt;

-- applyWithdrawalRefundRate()의 "있으면 갱신, 없으면 생성" 패턴이
-- DB 제약 없이 앱 로직(findByTuitionBillIdAndRefundType)만으로 중복을 막고 있어 동시요청 경쟁조건에 노출돼 있었다.
-- 같은 (tuition_bill_id, refund_type) 조합의 두 번째 INSERT를 DB가 직접 거부하게 한다.
SET @uk_refunds_tuition_bill_type_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND CONSTRAINT_NAME = 'uk_refunds_tuition_bill_type'
);
SET @uk_refunds_tuition_bill_type_ddl = IF(@uk_refunds_tuition_bill_type_exists = 0,
    'ALTER TABLE refunds ADD CONSTRAINT uk_refunds_tuition_bill_type UNIQUE (tuition_bill_id, refund_type)',
    'SELECT 1');
PREPARE uk_refunds_tuition_bill_type_stmt FROM @uk_refunds_tuition_bill_type_ddl;
EXECUTE uk_refunds_tuition_bill_type_stmt;
DEALLOCATE PREPARE uk_refunds_tuition_bill_type_stmt;

-- 4주차: 위 (tuition_bill_id, refund_type) 제약은 WITHDRAWAL(고지당 1건)에는 맞지만
-- EXCESS_DEPOSIT(분할납부 회차마다 별도 가상계좌가 초과입금될 수 있음, SCRUM-131)과
-- PG_CANCEL(고지 하나에 결제가 여러 건일 수 있어 결제별로 취소 요청이 생김, SCRUM-180)에는 너무 좁다.
-- 유형별로 실제 유일성 범위가 다르므로(WITHDRAWAL=고지당, EXCESS_DEPOSIT=가상계좌당, PG_CANCEL=결제당)
-- 생성 컬럼으로 유형별 키를 만들어 하나의 UNIQUE 인덱스로 세 조건을 동시에 표현한다.
SET @uk_refunds_tuition_bill_type_drop_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND CONSTRAINT_NAME = 'uk_refunds_tuition_bill_type'
);
SET @uk_refunds_tuition_bill_type_drop_ddl = IF(@uk_refunds_tuition_bill_type_drop_exists > 0,
    'ALTER TABLE refunds DROP INDEX uk_refunds_tuition_bill_type',
    'SELECT 1');
PREPARE uk_refunds_tuition_bill_type_drop_stmt FROM @uk_refunds_tuition_bill_type_drop_ddl;
EXECUTE uk_refunds_tuition_bill_type_drop_stmt;
DEALLOCATE PREPARE uk_refunds_tuition_bill_type_drop_stmt;

SET @refunds_dedup_key_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'refund_dedup_key'
);
SET @refunds_dedup_key_ddl = IF(@refunds_dedup_key_exists = 0,
    'ALTER TABLE refunds ADD COLUMN refund_dedup_key VARCHAR(50) GENERATED ALWAYS AS (
        CASE refund_type
            WHEN ''WITHDRAWAL'' THEN CONCAT(''TB:'', tuition_bill_id)
            WHEN ''EXCESS_DEPOSIT'' THEN CONCAT(''VA:'', virtual_account_id)
            WHEN ''PG_CANCEL'' THEN CONCAT(''PAY:'', payment_id)
        END
    ) STORED',
    'SELECT 1');
PREPARE refunds_dedup_key_stmt FROM @refunds_dedup_key_ddl;
EXECUTE refunds_dedup_key_stmt;
DEALLOCATE PREPARE refunds_dedup_key_stmt;

SET @uk_refunds_dedup_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND CONSTRAINT_NAME = 'uk_refunds_dedup'
);
SET @uk_refunds_dedup_ddl = IF(@uk_refunds_dedup_exists = 0,
    'ALTER TABLE refunds ADD CONSTRAINT uk_refunds_dedup UNIQUE (refund_dedup_key)',
    'SELECT 1');
PREPARE uk_refunds_dedup_stmt FROM @uk_refunds_dedup_ddl;
EXECUTE uk_refunds_dedup_stmt;
DEALLOCATE PREPARE uk_refunds_dedup_stmt;

-- 가상계좌 환불(WITHDRAWAL/EXCESS_DEPOSIT)의 토스 cancel 호출은 refundReceiveAccount(수취 계좌)가 필수다.
-- PG_CANCEL(카드)은 필요 없어 세 컬럼 모두 nullable로 두고 실행(execute) 시점에 채운다.
SET @refunds_refund_bank_code_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'refund_bank_code'
);
SET @refunds_refund_bank_code_ddl = IF(@refunds_refund_bank_code_exists = 0,
    'ALTER TABLE refunds
        ADD COLUMN refund_bank_code VARCHAR(10) COMMENT ''가상계좌 환불 수취은행 코드, PG_CANCEL은 NULL'',
        ADD COLUMN refund_account_number VARCHAR(20) COMMENT ''가상계좌 환불 수취계좌번호(하이픈 없이), PG_CANCEL은 NULL'',
        ADD COLUMN refund_holder_name VARCHAR(60) COMMENT ''가상계좌 환불 수취계좌 예금주명, PG_CANCEL은 NULL''',
    'SELECT 1');
PREPARE refunds_refund_bank_code_stmt FROM @refunds_refund_bank_code_ddl;
EXECUTE refunds_refund_bank_code_stmt;
DEALLOCATE PREPARE refunds_refund_bank_code_stmt;

-- refund_rate는 0~1 사이 비율인데 계산 로직 버그로 음수·1 초과값이 저장될 여지를 DB 레벨에서 막는다.
SET @chk_refunds_rate_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND CONSTRAINT_NAME = 'chk_refunds_rate'
);
SET @chk_refunds_rate_ddl = IF(@chk_refunds_rate_exists = 0,
    'ALTER TABLE refunds ADD CONSTRAINT chk_refunds_rate CHECK (refund_rate BETWEEN 0 AND 1)',
    'SELECT 1');
PREPARE chk_refunds_rate_stmt FROM @chk_refunds_rate_ddl;
EXECUTE chk_refunds_rate_stmt;
DEALLOCATE PREPARE chk_refunds_rate_stmt;

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
    status               VARCHAR(20) NOT NULL COMMENT 'SCHEDULED, PAID, OVERDUE',
    UNIQUE KEY uk_installment_plan_items_plan_round (installment_plan_id, round_no),
    CONSTRAINT fk_installment_plan_items_plan FOREIGN KEY (installment_plan_id) REFERENCES installment_plans (id),
    CONSTRAINT fk_installment_plan_items_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 결제 1건이 서로 다른 두 회차의 완납 처리에 쓰이지 않도록 이 컬럼에 UNIQUE를 걸 수도 있지만,
-- 그러면 결제 실패·방치 후 같은 회차를 다시 결제하려는 정상 재시도까지 DB 제약 위반으로 막힌다
-- (일반 전액결제도 같은 이유로 tuition_bill_id에 UNIQUE를 걸지 않는다). 대신 InstallmentPlanService.getItemOrThrow가
-- 이미 PAID인 회차의 신규 체크아웃 세션 생성을 막고, 기존 TuitionOverpaymentGuard가 합계 기준 이중 청구를 막는다.
SET @payments_installment_plan_item_id_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'installment_plan_item_id'
);
SET @payments_installment_plan_item_id_ddl = IF(@payments_installment_plan_item_id_exists = 0,
    'ALTER TABLE payments ADD COLUMN installment_plan_item_id BIGINT COMMENT ''분할납부 회차 결제일 때만 채워짐, installment_plan_items.id 참조''',
    'SELECT 1');
PREPARE payments_installment_plan_item_id_stmt FROM @payments_installment_plan_item_id_ddl;
EXECUTE payments_installment_plan_item_id_stmt;
DEALLOCATE PREPARE payments_installment_plan_item_id_stmt;

SET @fk_payments_installment_plan_item_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND CONSTRAINT_NAME = 'fk_payments_installment_plan_item'
);
SET @fk_payments_installment_plan_item_ddl = IF(@fk_payments_installment_plan_item_exists = 0,
    'ALTER TABLE payments ADD CONSTRAINT fk_payments_installment_plan_item FOREIGN KEY (installment_plan_item_id) REFERENCES installment_plan_items (id)',
    'SELECT 1');
PREPARE fk_payments_installment_plan_item_stmt FROM @fk_payments_installment_plan_item_ddl;
EXECUTE fk_payments_installment_plan_item_stmt;
DEALLOCATE PREPARE fk_payments_installment_plan_item_stmt;

-- virtual_accounts.installment_plan_item_id 컬럼은 위(가상계좌 섹션)에서 이미 추가했다. FK만 여기서 건다.
SET @fk_virtual_accounts_installment_plan_item_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'virtual_accounts' AND CONSTRAINT_NAME = 'fk_virtual_accounts_installment_plan_item'
);
SET @fk_virtual_accounts_installment_plan_item_ddl = IF(@fk_virtual_accounts_installment_plan_item_exists = 0,
    'ALTER TABLE virtual_accounts ADD CONSTRAINT fk_virtual_accounts_installment_plan_item FOREIGN KEY (installment_plan_item_id) REFERENCES installment_plan_items (id)',
    'SELECT 1');
PREPARE fk_virtual_accounts_installment_plan_item_stmt FROM @fk_virtual_accounts_installment_plan_item_ddl;
EXECUTE fk_virtual_accounts_installment_plan_item_stmt;
DEALLOCATE PREPARE fk_virtual_accounts_installment_plan_item_stmt;

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

-- Academic→Payment Kafka 이벤트 연동 스냅샷 (2026-09-04)
CREATE TABLE IF NOT EXISTS student_snapshots (
    student_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    department_name VARCHAR(100) NULL,
    source_version BIGINT NOT NULL,
    synced_at DATETIME NOT NULL,
    PRIMARY KEY (student_id),
    CONSTRAINT uk_student_snapshots_user_id UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS semester_snapshots (
    semester_id BIGINT NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    source_version BIGINT NOT NULL,
    synced_at DATETIME NOT NULL,
    PRIMARY KEY (semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS withdrawal_snapshots (
    withdrawal_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    source_version BIGINT NOT NULL,
    synced_at DATETIME NOT NULL,
    PRIMARY KEY (withdrawal_id),
    INDEX idx_withdrawal_snapshots_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2026-09-07: 자퇴 환불률 산정 장애 격리 - refunds.status에 PENDING_ACADEMIC_VERIFICATION/MANUAL_REVIEW_REQUIRED 추가.
-- 기존 CREATE TABLE 문(refunds, 위)은 손대지 않고 ALTER로만 반영한다 - "PENDING_ACADEMIC_VERIFICATION"이
-- 30자라 기존 VARCHAR(20)로는 길이가 부족해 폭을 넓힌다.
SET @refunds_status_length = (
    SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'status'
);
SET @refunds_status_widen_ddl = IF(@refunds_status_length < 32,
    'ALTER TABLE refunds MODIFY COLUMN status VARCHAR(32) NOT NULL COMMENT ''REQUESTED, SUCCEEDED, FAILED, RETRYING, PENDING_ACADEMIC_VERIFICATION, MANUAL_REVIEW_REQUIRED''',
    'SELECT 1');
PREPARE refunds_status_widen_stmt FROM @refunds_status_widen_ddl;
EXECUTE refunds_status_widen_stmt;
DEALLOCATE PREPARE refunds_status_widen_stmt;

-- 2026-09-08: 증명서 진위확인(공개 API) 조회 이력. documents 1건에 여러 번 조회될 수 있어 1:N.
CREATE TABLE IF NOT EXISTS document_verifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id  BIGINT NOT NULL,
    verified_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verifier_ip  VARCHAR(45) COMMENT 'IPv6 최대 길이, nullable',
    result       VARCHAR(20) NOT NULL COMMENT 'VALID, REVOKED, EXPIRED, SIGNATURE_MISMATCH',
    INDEX idx_document_verifications_document_id (document_id),
    CONSTRAINT fk_document_verifications_document FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2026-09-10: 등록금 고지 항목별 내역(수업료/학생회비 등). 백필은 migration/20260910_create_tuition_bill_items.sql 참고.
CREATE TABLE IF NOT EXISTS tuition_bill_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tuition_bill_id BIGINT NOT NULL,
    item_name       VARCHAR(50) NOT NULL,
    amount          DECIMAL(12, 0) NOT NULL,
    paid            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tuition_bill_items_tuition_bill_id (tuition_bill_id),
    CONSTRAINT fk_tuition_bill_items_tuition_bill FOREIGN KEY (tuition_bill_id) REFERENCES tuition_bills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
