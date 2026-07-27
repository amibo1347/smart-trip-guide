# 스마트 여행 플래너 (Smart Trip Guide)

여행 **계획 → 예약 → 현지 기록 → 회고**까지 한 앱에서 이어지도록 만든 여행 플래너입니다.
Spring Boot 백엔드 하나가 React PWA 프론트까지 함께 서빙하는 단일 서비스 구조이고,
폰에서 홈 화면에 설치해 GPS·카메라·마이크를 그대로 쓸 수 있게 PWA로 만들었습니다.

**운영 주소:** https://smart-trip-guide-production.up.railway.app

## 스택

| 구분 | 기술 |
|---|---|
| 백엔드 | Spring Boot 3.5.3 · Java 21 · Gradle |
| 데이터 | MySQL · JPA/Hibernate · **Flyway** 마이그레이션 |
| 인증 | Spring Security · OAuth2 Client(Google · Kakao) · Spring Session JDBC |
| 프론트 | React 18 · Vite · vite-plugin-pwa |
| 지도 | MapLibre GL · Leaflet |
| 외부 | Google Gemini API (일정 생성 · 번역 · 회고 리포트), jsoup (예약 링크 메타 파싱) |
| 배포 | Docker · Railway / Oracle Cloud(Caddy 자동 HTTPS) |

## 주요 기능

### 여행 · 일정
- 여행 생성(목적지 · 기간 · 인원) 후 **일자별 일정** 관리
- **AI 일정 생성** — Gemini로 목적지·기간에 맞는 일차별 코스를 초안으로 뽑고, 사용자가 수정
- 장소별 좌표를 저장해 일정 전체를 지도 위 경로로 시각화

### 예약
- **예약 링크 붙여넣기** → jsoup으로 Open Graph / JSON-LD 메타를 파싱해 제목 · 이미지 · 가격 미리보기
- 날짜 자동 채움(여행 기간 기준), 항공/숙소 예약 딥링크 핸드오프

### 예산
- 항목별 지출 등록과 카테고리별 합계, 실시간 **환율 변환**

### 기록 (여행 중)
- 사진 업로드 + GPS 좌표 기록 → 방문 지점을 지도에 누적
- 역지오코딩으로 좌표를 지명으로 변환해 표시
- **오프라인 큐** — 네트워크가 끊긴 곳에서 남긴 기록을 로컬에 쌓아 뒀다가 연결되면 자동 전송

### 회고 · 공유
- 여행 종료 후 평점 · 메모 작성, **Gemini 기반 여행 리포트** 자동 생성
- 공유 토큰으로 로그인 없이 볼 수 있는 읽기 전용 공유 페이지

### 번역 도우미 · 다국어
- 현지에서 바로 쓰는 번역 화면
- 앱 전체 i18n (한국어 / 영어)

## 구조

```
├── src/main/java/com/travel/planner/
│   ├── account/      # 회원가입·로그인·OAuth2
│   ├── trip/         # 여행
│   ├── planning/     # 일정 · AI 일정 생성 · 숙소/예약 링크
│   ├── booking/      # 예약(링크 메타 파싱 포함)
│   ├── budget/  currency/    # 예산 · 환율
│   ├── tracking/     # 사진 · GPS 기록 · 역지오코딩
│   ├── feedback/     # 회고 · AI 리포트
│   ├── share/        # 공유 링크
│   ├── translation/  # 번역
│   └── config/       # SPA 라우팅 · 보안 설정
├── src/main/resources/db/migration/   # Flyway V1~
└── frontend/         # React PWA (Vite) — gradle 빌드 시 static 으로 번들
```

Gradle `processResources` 단계에서 `frontend` 를 빌드해 Spring 정적 리소스로 복사하므로,
**jar 하나만 실행하면 API와 화면이 동시에 뜹니다.**

## 로컬 실행

MySQL이 필요합니다. docker compose로 함께 띄웁니다.

```bash
docker compose up -d      # MySQL
./gradlew bootRun         # 백엔드 + 프론트 번들 (http://localhost:8082)
```

프론트만 따로 개발할 때는:

```bash
cd frontend
npm install
npm run dev               # Vite dev server (백엔드로 프록시)
```

첫 기동 시 Flyway가 빈 DB에 스키마를 자동 생성합니다.

### 환경 변수

프로젝트 루트 `.env` (gitignore 처리됨):

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3308` / `smart_travel` | DB 접속 |
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | DB 계정 |
| `GEMINI_API_KEY` | — | AI 일정 · 번역 · 회고 리포트 (없으면 AI 기능만 비활성) |
| `GEMINI_MODEL` | `gemini-2.5-flash-lite` | 사용할 모델 |
| `UPLOAD_DIR` | `./uploads` | 기록 사진 저장 경로 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | — | 구글 로그인 (선택) |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | — | 카카오 로그인 (선택) |

소셜 로그인 값을 넣지 않으면 버튼만 비활성화되고 이메일 로그인은 정상 동작합니다.

## 배포

- **Railway** (현재 운영) — 리포의 `Dockerfile` 자동 감지 + MySQL 플러그인. [DEPLOY.md](./DEPLOY.md)
- **Oracle Cloud Always Free VM** — `docker-compose.prod.yml` 로 앱 + MySQL + Caddy(자동 HTTPS) 단일 VM 구성. [DEPLOY-oracle.md](./DEPLOY-oracle.md)

헬스체크 경로는 `/api/health` 입니다.

## 개발 정보

- 기간: 2026.06 ~ 2026.07
- 인원: 1명
- 환경: Windows · VS Code
- 화면 설계와 도메인 모델(ERD)을 먼저 잡은 뒤, 기능 명세를 바탕으로 **Claude Code** 와 함께 구현했습니다.
  ERD 설계는 [docs/01-erd-design.md](./docs/01-erd-design.md) 에 정리돼 있습니다.
