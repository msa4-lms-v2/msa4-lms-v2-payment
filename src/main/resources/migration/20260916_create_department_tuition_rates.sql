-- 학과와 학기는 Academic 식별자를 참조한다. 서비스 간 DB 외래키는 생성하지 않는다.
CREATE TABLE IF NOT EXISTS department_tuition_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    amount DECIMAL(12, 0) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_department_tuition_rates UNIQUE (department_id, semester_id),
    CONSTRAINT ck_department_tuition_rates_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기존 고지는 NULL을 유지하고 신규 자동 책정 고지만 적용 기준을 기록한다.
SET @tuition_rate_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tuition_bills' AND COLUMN_NAME = 'tuition_rate_id') = 0,
    'ALTER TABLE tuition_bills ADD COLUMN tuition_rate_id BIGINT NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE tuition_rate_stmt FROM @tuition_rate_ddl;
EXECUTE tuition_rate_stmt;
DEALLOCATE PREPARE tuition_rate_stmt;
