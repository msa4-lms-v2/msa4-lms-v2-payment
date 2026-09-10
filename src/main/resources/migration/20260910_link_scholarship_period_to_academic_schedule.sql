-- Academic 학사일정 이벤트로 생성되는 장학금 기간을 일정별로 멱등 upsert하기 위한 키와 활성 상태입니다.
ALTER TABLE scholarship_application_periods
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER academic_schedule_id,
    ADD CONSTRAINT uk_scholarship_application_periods_schedule UNIQUE (academic_schedule_id);
