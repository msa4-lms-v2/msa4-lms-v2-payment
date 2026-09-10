-- 2026-09-10: 등록금 고지 항목별 내역(수업료/학생회비 등) 추가
-- 기존 tuition_bills.billing_amount는 총액 단일값만 가지고 있었다. 학생 등록금 납부 화면에서
-- 항목별 금액·납입여부를 보여주기 위해 항목 테이블을 신설하고, 기존 고지는 전액을 '수업료' 한 항목으로 백필한다.

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

-- 재실행해도 중복 삽입되지 않도록, 아직 항목이 하나도 없는 고지에 대해서만 백필한다.
INSERT INTO tuition_bill_items (tuition_bill_id, item_name, amount, paid, created_at)
SELECT tb.id, '수업료', tb.billing_amount, (tb.status = 'PAID'), tb.created_at
FROM tuition_bills tb
WHERE NOT EXISTS (SELECT 1 FROM tuition_bill_items tbi WHERE tbi.tuition_bill_id = tb.id);
