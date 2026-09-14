-- 기존 주문/계좌 이력을 보존하고 현재 발급 주문만 고지에 저장한다. 운영에 자동 적용하지 않는다.
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tuition_bills' AND COLUMN_NAME='admission_order_id')=0,'ALTER TABLE tuition_bills ADD COLUMN admission_order_id VARCHAR(64) NULL','SELECT 1');
PREPARE admission_stmt FROM @ddl;
EXECUTE admission_stmt;
DEALLOCATE PREPARE admission_stmt;

-- 은행이 만료 계좌번호를 재사용할 수 있다. 식별/웹훅 멱등성은 기존 UNIQUE order_id와 PG paymentKey를 사용한다.
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='virtual_accounts' AND INDEX_NAME='uk_virtual_accounts_account_number')>0,'ALTER TABLE virtual_accounts DROP INDEX uk_virtual_accounts_account_number','SELECT 1');
PREPARE admission_stmt FROM @ddl;
EXECUTE admission_stmt;
DEALLOCATE PREPARE admission_stmt;
