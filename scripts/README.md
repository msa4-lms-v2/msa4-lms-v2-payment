# 입학 로컬 통합 검증

Academic/Auth/Payment/SCG 형제 저장소의 최신 `bootJar`, Java 21 이상, Node 20 이상과 Docker가 필요하다.

```powershell
node scripts/admission-e2e.mjs
```

별도 터미널에서 `http://127.0.0.1:18089/control/status`가 ready인지 확인한 뒤 `Invoke-RestMethod -Method Post http://127.0.0.1:18089/control/run`으로 실행한다. 정상 흐름의 실제 3분 재확인과 재발급 계좌의 알림 유실 복구를 포함하므로 수분이 걸린다. 결과는 workspace `analytics/evidence/msa4-lms-v2/admission-trusted-e2e-<실행시각>/result.json`에 저장되어 이전 검증 결과를 보존한다.

입학 전용 토큰은 주입하지 않는다. 신뢰된 내부 HTTP 호출 성공, 관리자 JWT를 포함한 SCG 내부 경로 차단, PG 미입금 상태의 계정 미생성, 완납 이후의 재시도·중복·재기동 복구를 함께 검증한다. 외부 직접 접근 차단은 운영 인프라에서 별도 확인해야 한다.

종료는 `Invoke-RestMethod -Method Post http://127.0.0.1:18089/control/stop`을 사용한다. 이 실행이 생성한 JVM과 소유 라벨을 확인한 컨테이너만 정리한다. 사용 중인 포트는 시작 전에 거부한다. 이전 비정상 종료의 컨테이너가 남았으면 이름과 라벨을 확인한 뒤 별도로 정리해야 한다.

PG는 loopback mock이며 실제 계좌·입금·환불을 실행하지 않는다. 서비스는 별도 DB/Redis/Kafka/MinIO 포트를 사용하고 프로필과 외부 주소를 테스트 값으로 고정한다. 로컬/공유 서비스의 `.env`·데이터를 사용하지 않는다. Auth의 추적된 기준 schema.sql이 없으므로 Account 매핑에 필요한 최소 스키마와 추적된 마이그레이션으로 fixture를 만든다. 배포 DB 전체 제약의 재현과 실제 PG 검증은 별도다.

Windows의 javapath 실행 래퍼 대신 실제 JVM을 실행하고, jar 사본을 사용하므로 테스트 중 다른 빌드가 기존 jar를 교체해도 실행에 영향을 주지 않는다.
