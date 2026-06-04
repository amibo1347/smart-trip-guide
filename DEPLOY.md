# Railway 배포 가이드 (핸드폰에서 실행)

이 앱은 **단일 컨테이너**(Spring Boot jar가 React PWA까지 서빙) + **MySQL**로 구성됩니다.
Railway가 리포지토리의 `Dockerfile`을 자동 감지해 빌드합니다. HTTPS 도메인이 제공되므로
폰에서 PWA 설치·GPS·마이크·카메라가 모두 동작합니다.

> 사전 준비: GitHub 리포(`amibo1347/smart-trip-guide`, 브랜치 `all`)에 푸시 완료, Railway 계정.

## 1. 앱 서비스 배포
1. [railway.app](https://railway.app) → **New Project → Deploy from GitHub repo** → 이 리포 선택, 브랜치 `all`.
2. Railway가 루트의 `Dockerfile`을 감지해 빌드(프론트 빌드 포함, 수 분 소요).

## 2. MySQL 추가
1. 같은 프로젝트에서 **New → Database → Add MySQL**.
2. 자동으로 `MYSQLHOST/MYSQLPORT/MYSQLDATABASE/MYSQLUSER/MYSQLPASSWORD` 변수가 생깁니다.

## 3. 앱 서비스 환경변수 (Variables 탭)
> Railway 변수 참조 문법: `${{MySQL.변수명}}` (MySQL 서비스 이름이 다르면 그 이름으로).

| 변수 | 값 | 비고 |
|---|---|---|
| `GEMINI_API_KEY` | (본인 키) | **필수** — AI 일정/번역/회고. 무료 티어 키 권장 |
| `DB_HOST` | `${{MySQL.MYSQLHOST}}` | 내부 네트워크 호스트 |
| `DB_PORT` | `${{MySQL.MYSQLPORT}}` | |
| `DB_NAME` | `${{MySQL.MYSQLDATABASE}}` | |
| `DB_USER` | `${{MySQL.MYSQLUSER}}` | |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` | |
| `GEMINI_MODEL` | `gemini-2.5-flash-lite` | (선택) 기본값과 동일 |

- `SPRING_PROFILES_ACTIVE=prod`, 포트(`PORT`)는 **Dockerfile/플랫폼이 자동 처리** — 직접 설정 불필요.
- 첫 기동 시 Flyway(V1~V15)가 빈 DB에 스키마를 자동 생성합니다.

## 4. 도메인 생성 & 접속
1. 앱 서비스 → **Settings → Networking → Generate Domain** → `https://<앱>.up.railway.app`.
2. 폰 브라우저로 접속 → **공유 → 홈 화면에 추가**(PWA 설치). 이메일/비밀번호로 가입·로그인.

## 5. (선택) 소셜 로그인
도메인이 정해진 뒤에만 가능합니다. 구글/카카오 개발자 콘솔에 리다이렉트 URI 추가:
- Google: `https://<도메인>/login/oauth2/code/google`
- Kakao: `https://<도메인>/login/oauth2/code/kakao`

그 후 변수 추가: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`.
> 설정하지 않으면 소셜 버튼만 비활성, 이메일 로그인은 정상 동작합니다.

## 6. (선택) 사진 영구 보관
PaaS 파일시스템은 휘발성이라 **재배포 시 업로드 사진이 사라집니다**. 영구 보관하려면:
1. 앱 서비스 → **Volumes → New Volume**, mount path `/data`.
2. 변수 `UPLOAD_DIR=/data/uploads` 추가.

## 참고 / 주의
- **무료 한도**: Railway 무료는 사용 크레딧 한도가 있습니다(초과 시 일시 중지). 트래픽이 적은 개인용엔 충분.
- **헬스체크 경로**: `/api/health` (Settings → Healthcheck Path 에 지정 가능).
- **DB 드라이버**: 연결 URL에 `useSSL=false&allowPublicKeyRetrieval=true` 가 포함돼 MySQL 8 인증과 호환됩니다(내부 네트워크 기준).
- 로컬 개발은 그대로 `docker compose up -d` + `./dev.ps1` 사용(영향 없음).
