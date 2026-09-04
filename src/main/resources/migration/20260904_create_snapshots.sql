-- 2026-09-04: Academic→Payment Kafka 이벤트 연동 - 구독 측 스냅샷 테이블
-- Academic이 발행하는 StudentSnapshotChanged/SemesterCreated/WithdrawalApproved를
-- 구독해 채우는 로컬 스냅샷. Payment는 이 테이블만 보고 Academic을 직접 호출하지 않는다.

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
