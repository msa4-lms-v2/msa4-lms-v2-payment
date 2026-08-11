# LMS-Payment

## Gateway 사용자 컨텍스트

Payment는 클라이언트 JWT를 직접 검증하지 않는다. SCG가 JWT 검증 후 다음 헤더를 생성하고 HMAC으로 서명해야 한다.

- `X-User-Id`
- `X-User-Role`
- `X-Gateway-Timestamp`: Unix epoch seconds
- `X-Gateway-Signature`: HMAC-SHA256 결과의 Base64URL 인코딩(패딩 없음)

서명 입력은 아래 값을 개행으로 연결한다.

```text
{userId}\n{role}\n{timestamp}\n{HTTP_METHOD}\n{requestURI}
```

`HTTP_METHOD`는 대문자이며 `requestURI`에는 쿼리 문자열을 포함하지 않는다. SCG와 Payment에 동일한 32바이트 이상의 `GATEWAY_CONTEXT_SECRET`을 환경변수로 주입해야 한다. 기본 허용 시간 오차는 2분이며 `GATEWAY_CONTEXT_ALLOWED_CLOCK_SKEW`로 조정할 수 있다.

서명 환경변수가 없거나 헤더가 변조·만료되면 보호 API는 `401`을 반환한다.

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
