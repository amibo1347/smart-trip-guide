# 스마트 여행 계획 및 기록 서비스 — 데이터베이스 설계 (v1)

> 여행 **전(계획) → 중(기록) → 후(복기) → 피드백 학습**의 데이터 흐름을 하나로 잇는 것이 설계의 핵심.
> Stack: Spring Boot + Spring Data JPA + MySQL 8

---

## 1. 도메인 구조 (Bounded Context)

| 영역 | 책임 | 핵심 엔티티 |
|------|------|------------|
| **Account** | 사용자/인증 | `User` |
| **Planning (여행 전)** | AI 계획 생성·저장 | `Trip`, `Plan`, `PlanDay`, `PlanItem`, `Place`, `Accommodation` |
| **Tracking (여행 중)** | 실시간 기록 | `LocationLog`, `MoodLog`, `Expense`, `Photo` |
| **Review (여행 후)** | 매핑·시각화 | (Tracking 데이터를 조회/집계 — 신규 테이블 최소) |
| **Feedback (성장 루프)** | 계획 vs 실제 비교·학습 | `TripFeedback`, `PlanItemActual`, `UserPreferenceProfile` |

---

## 2. 핵심 엔티티 정의

### 2.1 Account

```
User
- id            BIGINT PK
- email         VARCHAR(255) UNIQUE
- password_hash VARCHAR(255)
- nickname      VARCHAR(50)
- created_at    DATETIME
```

### 2.2 Planning (여행 전)

`Trip`은 모든 단계를 묶는 **루트 애그리거트**. 계획·기록·복기·피드백이 전부 Trip 하위로 매달린다.

```
Trip  (여행 1건 = 전 과정의 컨테이너)
- id           BIGINT PK
- user_id      FK -> User
- title        VARCHAR(100)
- start_date   DATE
- end_date     DATE
- headcount    INT            -- 인원
- budget_limit DECIMAL(12,2)  -- 예산 한도
- concept      VARCHAR(50)    -- 여행 컨셉(휴양/액티비티/맛집...) 또는 별도 코드 테이블
- status       ENUM(PLANNED, ONGOING, DONE)
- created_at   DATETIME

Plan  (AI가 생성한 일정 1버전. Trip:Plan = 1:N 으로 재생성 이력 보관)
- id           BIGINT PK
- trip_id      FK -> Trip
- version      INT            -- 1,2,3... 재추천 시 증가
- generated_by ENUM(AI, USER) -- AI 생성/수동 편집 구분
- ai_model     VARCHAR(50)    -- 어떤 모델/프롬프트로 생성됐는지
- prompt_snapshot JSON        -- 생성 당시 입력조건 스냅샷(예산/기간/컨셉)
- created_at   DATETIME

PlanDay  (일자별)
- id           BIGINT PK
- plan_id      FK -> Plan
- day_no       INT            -- 1일차, 2일차
- date         DATE

PlanItem  (일자 안의 개별 일정: 장소 방문/식사/이동/숙박)
- id            BIGINT PK
- plan_day_id   FK -> PlanDay
- place_id      FK -> Place (nullable)
- type          ENUM(SPOT, MEAL, MOVE, STAY, ACTIVITY)
- title         VARCHAR(100)
- planned_start TIME
- planned_end   TIME
- est_cost      DECIMAL(10,2)  -- 예상 비용
- sort_order    INT

Place  (장소 마스터 — 외부 지도 API 결과 캐시. 재사용/중복방지 목적)
- id            BIGINT PK
- provider      ENUM(GOOGLE, KAKAO)
- provider_place_id VARCHAR(100)   -- 외부 API의 place id
- name          VARCHAR(150)
- category      VARCHAR(50)
- latitude      DECIMAL(10,7)
- longitude     DECIMAL(10,7)
- address       VARCHAR(255)
- UNIQUE(provider, provider_place_id)

Accommodation  (숙소 — Place의 특수형. Place로 통합하고 type으로 구분해도 됨)
- id            BIGINT PK
- trip_id       FK -> Trip
- place_id      FK -> Place
- check_in      DATE
- check_out     DATE
- cost          DECIMAL(10,2)
```

### 2.3 Tracking (여행 중) — **쓰기 부하 집중 구간**

```
LocationLog  (기록 버튼 → 시각 + GPS 1건)
- id          BIGINT PK
- trip_id     FK -> Trip
- recorded_at DATETIME       -- 클라이언트 시각(서버 수신 시각 별도 권장)
- latitude    DECIMAL(10,7)
- longitude   DECIMAL(10,7)
- accuracy_m  FLOAT          -- GPS 정확도(노이즈 필터링용)
- place_id    FK -> Place (nullable, 사후 역지오코딩으로 채움)

MoodLog  (이모지 1탭 기록)
- id          BIGINT PK
- trip_id     FK -> Trip
- location_log_id FK -> LocationLog (nullable, 위치와 묶을 수 있음)
- emoji       VARCHAR(16)
- recorded_at DATETIME

Expense  (간편 가계부)
- id          BIGINT PK
- trip_id     FK -> Trip
- amount      DECIMAL(10,2)
- category    VARCHAR(30)    -- 식비/교통/숙박/기타
- memo        VARCHAR(100)
- location_log_id FK (nullable)
- spent_at    DATETIME

Photo  (사진 — 위치/시각 기준 자동 매핑)
- id          BIGINT PK
- trip_id     FK -> Trip
- file_url    VARCHAR(500)   -- 원본은 S3 등 오브젝트 스토리지, DB엔 메타만
- taken_at    DATETIME       -- EXIF 촬영시각
- latitude    DECIMAL(10,7)  -- EXIF GPS (없으면 시각 기준 매핑)
- longitude   DECIMAL(10,7)
- location_log_id FK (nullable)
```

### 2.4 Feedback (성장형 루프) — **차별화 포인트**

```
PlanItemActual  (계획 항목 vs 실제 비교)
- id            BIGINT PK
- plan_item_id  FK -> PlanItem
- visited       BOOLEAN        -- 실제 방문 여부
- actual_cost   DECIMAL(10,2)  -- Expense 집계 연동
- actual_start  DATETIME       -- LocationLog 기반 추정
- satisfaction  TINYINT        -- 1~5 만족도 (이모지/별점)

TripFeedback  (여행 단위 회고)
- id            BIGINT PK
- trip_id       FK -> Trip
- overall_score TINYINT        -- 1~5
- budget_diff   DECIMAL(12,2)  -- 계획 예산 - 실제 지출
- comment       TEXT

UserPreferenceProfile  (학습된 개인화 프로필 — 다음 추천 입력)
- id            BIGINT PK
- user_id       FK -> User UNIQUE
- preference_json JSON         -- 선호 카테고리/시간대/예산성향 가중치
- updated_at    DATETIME
```

---

## 3. 관계 요약 (ERD)

```
User 1───* Trip
Trip 1───* Plan ───* PlanDay ───* PlanItem ──? Place
Trip 1───* Accommodation ──? Place
Trip 1───* LocationLog ──? Place
Trip 1───* MoodLog / Expense / Photo   (각각 ──? LocationLog)
PlanItem 1───1 PlanItemActual
Trip 1───1 TripFeedback
User 1───1 UserPreferenceProfile
```

`──?` = nullable FK(선택적 연결), `──*` = 1:N

---

## 4. 기술적 병목 & 예상 난이도

### 🔴 A. GPS 쓰기 부하 + 노이즈
- "여행 중" LocationLog는 **빈번한 INSERT** 구간. 다중 사용자 동시 트래킹 시 쓰기 집중.
- 대응: ① 기록은 **버튼 탭 기준**(연속 폴링 아님)이라 빈도 관리 가능 ② `accuracy_m` 기준 노이즈 필터 ③ 배치 INSERT/큐(메시지 브로커) 고려.
- GPS 좌표는 `DECIMAL(10,7)`로 고정(약 1cm 정밀도). FLOAT 쓰면 미세 오차 누적됨.

### 🔴 B. 오프라인/네트워크 단절
- 해외·산간 여행지는 네트워크 불안정. **여행 중 기록이 유실되면 서비스 신뢰 붕괴.**
- 대응: 클라이언트 **로컬 큐(IndexedDB/SQLite)에 먼저 저장 후 동기화**. 서버는 `client_uuid` + 멱등키로 중복 INSERT 방지 필수.

### 🟠 C. AI 일정 생성의 비결정성·지연·비용
- LLM 응답은 느리고(수초~수십초), 비싸고, 출력 포맷이 흔들림.
- 대응: ① **비동기 처리**(생성 요청 → 폴링/웹소켓으로 결과 통보) ② 응답을 **JSON 스키마로 강제**(function calling/structured output) ③ `prompt_snapshot` 저장으로 재현성 확보 ④ 예산·동선 검증은 **AI 출력 후 서버 로직으로 재검증**(LLM은 산수·거리계산을 자주 틀림).

### 🟠 D. 사진 ↔ 위치 자동 매핑
- EXIF에 GPS가 없는 사진(스크린샷, 일부 기기)이 많음.
- 대응: GPS 없으면 **촬영시각(taken_at) 기준으로 가장 가까운 LocationLog에 매칭**하는 폴백 알고리즘. 사진 원본은 **DB가 아닌 오브젝트 스토리지(S3)**, DB엔 URL/메타만.

### 🟠 E. 지도 API 비용·할당량
- Google/Kakao 지도 호출은 과금·일일 한도 존재. 매 조회마다 외부 호출하면 비용 폭증.
- 대응: `Place` 테이블로 **역지오코딩/장소 결과 캐싱**, `(provider, provider_place_id)` 유니크로 중복 호출 차단.

### 🟢 F. 피드백 루프의 데이터 정합성
- "계획 vs 실제" 비교는 PlanItem ↔ LocationLog ↔ Expense를 **시간/위치로 연결**해야 함 — 자동 매칭이 핵심 난제.
- 대응: 1차 버전은 **반자동**(사용자가 "이 일정 다녀옴" 체크) → 데이터 쌓이면 시각·반경 기반 자동 추정으로 고도화.

---

## 5. 설계 시 권장 사항
1. **Trip을 루트 애그리거트로** — 모든 하위 데이터에 `trip_id`를 직접 두면 복기/통계 쿼리가 단순해짐.
2. **금액은 전부 `DECIMAL`** — `DOUBLE` 금지(부동소수 오차).
3. **시각은 두 종류 보관** — 클라이언트 기록 시각 + 서버 수신 시각(시차/조작 대비).
4. **소프트 삭제 + created_at/updated_at** 공통 컬럼(`BaseEntity`)로 통일.
5. `Place`와 `Accommodation`은 **하나로 합치고 type 구분**하는 방안도 검토(엔티티 수 절감).
```
