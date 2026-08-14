# msa4-lms-v2-payment 작업 리포트

## 2026-08-14 API 경로 정렬, 동시성 방어, 원자성 보강

### 요청

- 2026-08-14 재검수(`docs-v2` 감사)에서 확정한 4.3·4.4·4.6·5.1을 구현했다.

### 변경 내용

- **4.3 API 경로 정렬**: `PaymentController`의 체크아웃 세션 생성을 `POST /api/payment/checkout-session`에서 `POST /api/payment/payments`로, PG 승인을 `POST /api/payment/pg-requests`에서 `POST /api/payment/payments/confirm`으로, 결과 수동 동기화를 `PATCH /api/payment/payment-results`에서 `POST /api/payment/payments/{paymentId}/reconciliation`으로 옮겼다(요청 본문의 `orderId`를 경로의 `paymentId`로 대체). `TuitionBillController`의 목록 조회도 `GET /admin-tuition-bills`→`GET /tuition-bills`, `GET /student-tuition`→`GET /me/tuition-bills`로 정리했다.
- **4.4 초과 납부 방지**: `TuitionOverpaymentGuard`(별도 Bean)가 결제 확정 직전에 등록금 고지 행을 비관적 락으로 잠그고 현재 순납부액과 기존 성공 결제 합계를 다시 계산해, 신규 결제까지 합산했을 때 초과하면 거부한다.
- **4.6 원자성**: `TuitionBillRecorder`, `VirtualAccountRecorder`, `RefundRecorder`(각각 별도 Bean)를 추가해 등록금 고지 생성·가상계좌 발급·환불률 적용·환불 재시도의 저장과 감사 로그 기록을 하나의 트랜잭션으로 묶었다. `PaymentResultRecorder`와 동일한 패턴이며, self-invocation으로 `@Transactional`이 무시되는 문제를 피하려고 별도 Bean으로 분리했다.
- **5.1 장학금 동시 적용 방지**: `TuitionBillService.getTuitionBillForUpdateOrThrow()`(비관적 락)를 추가해, 장학금 적용 시 고지 행을 잠그고 기존 장학금 합계를 다시 계산한 뒤 초과 여부를 검증한다.

### 브랜치

- `feature/payment-api-path-alignment`, `feature/tuition-bill-list-path-alignment` — 4.3
- `feature/tuition-overpayment-guard` — 4.4
- `feature/atomic-status-and-audit-log` — 4.6
- `feature/scholarship-concurrent-allocation` — 5.1

### 미완료

- 4.2(Payment→Academic 자퇴 조회), 4.5(자퇴 환불액 계산)는 Academic에 자퇴 도메인 자체가 없어 보류했다. `docs-v2/백로그.md` 참고.
- 4.7(멱등키 복구, Outbox worker 재사용)은 스케줄러 인프라가 필요한 별도 작업 범위라 보류했다.

### 검증

- 모든 브랜치에서 `compileJava`/`compileTestJava` 통과, 영향받는 단위 테스트(PaymentServiceTest, ScholarshipServiceTest, TuitionBillServiceTest, VirtualAccountServiceTest, RefundServiceTest, OpenApiRuntimeContractTest, IdempotencyServiceTest 등) 전부 통과 확인.
- push는 하지 않았다.
