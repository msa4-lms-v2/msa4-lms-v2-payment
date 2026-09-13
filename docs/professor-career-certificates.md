# 교수 경력 증명서

교수로 로그인한 사용자가 요청 본문 없이 호출한다.

| 요청 | 결과 |
| --- | --- |
| POST /api/payment/career-certificates | 등록 임용연도·현재 소속·등록 상태를 담은 경력증명서 |
| POST /api/payment/lecture-career-certificates | 종료된 담당 강의와 학기별 강의 기간을 담은 강의경력증명서 |

기존 발급 응답과 동일하게 문서 ID를 반환한다. 기존
`GET /api/payment/certificates/{documentId}/download`로 다운로드하고,
기존 검증 토큰·QR·폐기 처리 흐름을 사용한다. 다른 교수의 문서는 다운로드할 수 없다.

## 데이터와 설정

Academic의 `GET /api/academic/professors/me/certificate-career`가 로그인 주체에
해당하는 교수만 조회한다. 강의는 CLOSED 상태이고 학기 종료일이 오늘 이하인 경우만 포함한다.
임용연도가 없거나 미래이면 경력 발급을 거부하며, 강의경력이 없으면 해당 증명서를 발급하지 않는다.
DB에 없는 퇴직일·총 근속기간·외부기관 경력은 계산하거나 표시하지 않는다.

Payment의 `ACADEMIC_INTERNAL_BASE_URL` 기본값은 `http://localhost:8082`이다.
컨테이너 배포에서는 Academic 내부 주소로 설정해야 한다. Payment가 검증된
X-User-Id/X-User-Role을 전달하므로 Academic 포트는 신뢰하는 내부 서비스에서만 접근해야 한다.
외부 요청은 기존 Gateway 인증을 거친다. 이번 변경은 인증 예외 경로를 추가하지 않는다.

Academic과 Payment를 함께 반영하고 재시작한 후 프론트를 반영한다.
문서 유형 CAREER, LECTURE_CAREER가 추가되며 기존 VARCHAR(30) 컬럼을 사용한다.
실제 배포 DB를 ENUM으로 별도 관리한다면 두 값을 먼저 허용해야 한다.

확인할 항목: 교수 본인 발급·다운로드, 다른 교수 다운로드 거부,
강의 이력 없음 오류, 긴 강의 목록의 PDF 페이지 분할, 기존 재직증명서 발급.
공용 DB/MinIO를 사용하는 실제 발급 검증은 로컬 단위 테스트와 별도로 수행한다.
