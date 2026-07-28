# 운영 배포 — 오라클 서버(intranet1) 공용 VM

**https://smart-planner.o-r.kr**

이 서버에는 team(인트라넷)·musiclv(악기몰)·loa(Next.js)·portfolio 가 이미 살고 있고,
**Docker 를 쓰지 않는다.** systemd + nginx + certbot 이 이 서버의 방식이라 플래너도 그대로 따랐다.
(리포의 `DEPLOY-oracle.md` / `DEPLOY-oracle-micro.md` 는 Docker 단독 VM 용 — 이 서버에는 해당 없음)

| 항목 | 값 |
|---|---|
| 호스트 | `oracle-server` (`134.185.123.138`, Oracle Linux 9, A1.Flex ARM 2 OCPU / 12GB) |
| 앱 | `/opt/planner/planner.jar` — 프론트 포함 단일 jar |
| 서비스 | `planner.service` (부팅 자동시작, `Restart=always`) |
| 포트 | `127.0.0.1:8082` — 외부 비공개, nginx 만 접근 |
| nginx | `/etc/nginx/conf.d/planner.conf` |
| DB | MariaDB `smart_travel` / 계정 `planner`@localhost (musiclv 와 같은 인스턴스 공유) |
| 업로드 | `/opt/planner/uploads` — jar 를 갈아끼워도 유지 |
| 환경변수 | `/opt/planner/planner.env` (모드 600, git 에 없음) |
| 인증서 | Let's Encrypt, certbot 자동 갱신 |

포트는 이 서버에서 8080(team) / 8081(musiclv) / 3000(loa) 이 쓰이고 있어 **8082** 를 잡았다.
힙은 `-Xmx512m` — team 1024m, musiclv 512m 과 12GB 를 나눠 쓰는 것을 고려한 값이다.

---

## 재배포

```powershell
.\deploy.ps1          # 빌드 → 업로드 → 재시작 → 헬스체크
.\deploy.ps1 -SkipBuild
```

빌드는 **로컬에서** 한다(서버에서 gradle+npm 을 돌리면 2 코어를 다른 앱과 경합).
스크립트가 처리하는 것:
- 로컬에서 `java -jar …jar` 로 앱이 떠 있으면 파일이 잠겨 `clean` 이 실패한다 → 먼저 종료
- `planner.jar.new` 로 올린 뒤 `mv` 로 교체 → 업로드 도중 서비스가 반쯤 쓰인 jar 를 잡지 않는다

## MariaDB 에 대해

앱은 MySQL 드라이버(`com.mysql:mysql-connector-j`) + `flyway-mysql` 을 쓰지만 **MariaDB 10.5 에서 정상 동작한다.**
마이그레이션 27개 전부 적용되고 Hibernate `ddl-auto: validate` 도 통과한다. 로그의 경고 2건은 무시해도 된다:

- `Name 'SPRING_SESSION_PK' ignored for PRIMARY key` — MariaDB 는 PK 이름을 항상 `PRIMARY` 로 둔다
- `The 5.5.5 version for MySQLDialect is no longer supported` — MariaDB 가 구버전 클라이언트 호환을 위해
  버전을 `5.5.5-10.5.29` 로 보고하는 관례 때문. 실제 기능 영향 없음

새 마이그레이션을 쓸 때만 주의: **MySQL 8 전용 문법**(`utf8mb4_0900_*` collation, 함수 인덱스,
`JSON_TABLE`)은 MariaDB 10.5 에서 깨진다. 지금까지의 V1~V27 에는 없다.

## nginx 뒤에 있다는 것 (중요)

`application-prod.yml` 의 `server.forward-headers-strategy: framework` 가 반드시 켜져 있어야 한다.
없으면 Spring 이 요청 스킴을 `http` 로 보고 **OAuth2 `redirect_uri` 를 `http://smart-planner.o-r.kr/...` 로**
만들어 구글·카카오가 로그인을 거부한다(공유 링크도 http 로 나간다). 확인:

```bash
ssh oracle-server "curl -s -o /dev/null -w '%{redirect_url}\n' https://smart-planner.o-r.kr/oauth2/authorization/kakao"
# redirect_uri 가 https:// 로 나와야 정상
```

프록시 헤더를 신뢰하는 만큼 앱은 `SERVER_ADDRESS=127.0.0.1` 로 루프백에만 바인딩한다
(`planner.env`). 외부에서 8082 로 직접 붙어 `X-Forwarded-*` 를 위조하는 경로를 막는다.

## 소셜 로그인

`SPRING_PROFILES_ACTIVE=prod,google,kakao` 로 켜져 있다. 콘솔에 운영 리디렉션 URI 를 등록해야 동작한다:

- Google Cloud Console → OAuth 클라이언트 → `https://smart-planner.o-r.kr/login/oauth2/code/google`
- Kakao Developers → 카카오 로그인 → `https://smart-planner.o-r.kr/login/oauth2/code/kakao`
- Kakao → 플랫폼 → Web 사이트 도메인에 `https://smart-planner.o-r.kr` 추가
  (`VITE_KAKAO_JS_KEY` 가 비어 있어 지금은 '카톡 공유'가 링크 복사로 폴백된다)

## 운영

| 작업 | 명령 |
|---|---|
| 상태 | `ssh oracle-server "systemctl status planner"` |
| 로그 | `ssh oracle-server "sudo journalctl -u planner -f"` |
| 재시작 | `ssh oracle-server "sudo systemctl restart planner"` |
| 환경변수 수정 | `ssh oracle-server "nano /opt/planner/planner.env"` → 재시작 |
| 메모리 | `ssh oracle-server "free -h; systemctl show -p MemoryCurrent planner"` |
| 백업 | `ssh oracle-server "sudo mysqldump smart_travel" > backup.sql` |
| 인증서 갱신확인 | `ssh oracle-server "sudo certbot certificates"` (자동 갱신됨) |

nginx 설정에서 신경 쓴 것:
- `proxy_read_timeout 180s` — AI 일정 생성이 Gemini 응답을 기다리므로 기본 60s 로는 짧다
- `client_max_body_size 20m` — 사진 업로드(multipart 12MB) 여유
- `/sw.js` 는 `no-cache` — 서비스워커가 캐시되면 PWA 가 새 버전을 영영 못 받는다
- `/assets/` 는 1년 immutable — Vite 해시 파일명이라 안전
