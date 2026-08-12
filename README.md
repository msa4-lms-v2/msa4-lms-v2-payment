# LMS-Payment

## Gateway 사용자 컨텍스트

Payment는 클라이언트 JWT를 직접 검증하지 않는다. SCG가 JWT 검증 후 다음 헤더를 생성해 전달한다.

- `X-User-Id`
- `X-User-Role`

서명 검증은 하지 않는다 - 인프라 단(네트워크 격리)에서 Payment에 SCG 외의 접근을 차단하는 것을 전제로 한다. 두 헤더가 없거나 형식이 올바르지 않으면(`X-User-Id`가 양의 정수가 아니거나 `X-User-Role`이 `STUDENT`/`PROFESSOR`/`ADMIN`/`SYSTEM`이 아니면) 보호 API는 `401`을 반환한다.

## Academic 연동

- 기본 로컬 주소: `http://localhost:8082`
- 운영 주소: `ACADEMIC_SERVICE_BASE_URL`
- 서비스 토큰: `ACADEMIC_SERVICE_TOKEN`
- 응답 형식: `code`, `message`, `data` (`code = "00"`만 성공)

Academic 없이 Payment 기능을 단독 테스트할 때만 `stub` 프로필을 명시적으로 활성화한다.

```powershell
$env:SPRING_PROFILES_ACTIVE='stub'
.\gradlew.bat bootRun
```
