# Payment Service

## 환경 변수
`.env` 파일이나 실행 환경변수에 다음을 구성합니다.
```env
SPRING_PROFILES_ACTIVE=local
DB_HOST=localhost
DB_PORT=3306
DB_NAME=payment_db
DB_USER=root
DB_PASSWORD=secret
JWT_SECRET=your_jwt_secret_key_here
```

## 기동 명령
```bash
./gradlew bootRun
```

## 테스트
```bash
./gradlew test
```
