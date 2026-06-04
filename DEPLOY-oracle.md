# Oracle Cloud Always Free VM 배포 가이드 (무료·항상 켜짐)

`docker-compose.prod.yml` 로 **앱 + MySQL + Caddy(자동 HTTPS)** 를 단일 VM에 띄웁니다.
ARM(A1) 무료 VM 기준이며, 무료 도메인(DuckDNS)으로 HTTPS까지 자동 처리됩니다.

> 핵심 파일: `Dockerfile`, `docker-compose.prod.yml`, `Caddyfile`, `.env.prod.example`

---

## 0. 준비물
- Oracle Cloud 계정(Always Free)
- 무료 도메인: [duckdns.org](https://www.duckdns.org) 로그인 → 서브도메인 1개 생성(예: `myapp`)
- 본인 `GEMINI_API_KEY`

## 1. VM 생성
1. Oracle Cloud Console → **Compute → Instances → Create Instance**.
2. 이미지/Shape:
   - Image: **Ubuntu 22.04**
   - Shape: **Ampere → VM.Standard.A1.Flex** (ARM), 예: **2 OCPU / 12 GB** (Always Free 한도 4 OCPU/24GB 내).
   - ⚠️ AMD Micro(1GB)는 빌드(gradle+node)가 메모리 부족으로 실패합니다 — **반드시 ARM A1** 권장.
   - ⚠️ A1이 "out of capacity"로 안 만들어지면 다른 **Availability Domain** 이나 리전을 시도하세요(무료 A1은 인기라 종종 만석).
3. SSH 공개키 등록 → 생성. **Public IP** 메모.

## 2. 포트 열기 (Oracle은 방화벽이 2겹 — 둘 다 해야 함)
**(A) 클라우드 레벨** — VCN → 해당 Subnet의 **Security List → Ingress Rules** 추가:
- TCP **80** (0.0.0.0/0)
- TCP **443** (0.0.0.0/0)
(22는 기본 열림)

**(B) VM OS 레벨** — SSH 접속 후(Ubuntu, iptables 기본 차단):
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```
> 이 OS 방화벽 단계를 빼먹으면 접속이 안 됩니다(가장 흔한 실수).

## 3. 도메인 연결 (DuckDNS)
DuckDNS 페이지에서 만든 서브도메인의 **current ip** 칸에 VM Public IP 입력 → update.
→ `myapp.duckdns.org` 가 VM을 가리킵니다.

## 4. Docker 설치 (VM에서)
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# 그룹 적용 위해 로그아웃 후 재접속(또는: newgrp docker)
```

## 5. 코드 받기 + 환경변수
```bash
git clone https://github.com/amibo1347/smart-trip-guide.git
cd smart-trip-guide
git checkout all
cp .env.prod.example .env
nano .env     # APP_DOMAIN, DB_PASSWORD, GEMINI_API_KEY 채우기
```

## 6. 빌드 & 실행
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
- 첫 빌드는 프론트(npm)+백엔드(gradle) 때문에 수 분 걸립니다.
- 로그 확인:
```bash
docker compose -f docker-compose.prod.yml logs -f app
```
`Started SmartTravelPlannerApplication` 이 보이면 기동 완료.
Caddy가 인증서를 받는 데 수십 초 걸릴 수 있습니다:
```bash
docker compose -f docker-compose.prod.yml logs -f caddy
```

## 7. 폰에서 접속
폰 브라우저로 **`https://myapp.duckdns.org`** → **공유 → 홈 화면에 추가**(PWA 설치) → 이메일로 가입·로그인.
→ GPS·마이크·카메라·번역 전부 동작합니다.

## 8. (선택) 소셜 로그인
구글/카카오 콘솔에 리다이렉트 URI 추가:
- `https://myapp.duckdns.org/login/oauth2/code/google`
- `https://myapp.duckdns.org/login/oauth2/code/kakao`

그리고 `.env` 에 `GOOGLE_CLIENT_ID/SECRET`, `KAKAO_CLIENT_ID/SECRET` 채운 뒤:
```bash
docker compose -f docker-compose.prod.yml up -d
```

---

## 운영 팁
| 작업 | 명령 |
|---|---|
| 상태 보기 | `docker compose -f docker-compose.prod.yml ps` |
| 로그 | `docker compose -f docker-compose.prod.yml logs -f app` |
| 코드 업데이트 후 재배포 | `git pull && docker compose -f docker-compose.prod.yml up -d --build` |
| 중지 | `docker compose -f docker-compose.prod.yml down` (볼륨/데이터는 유지) |
| 완전 삭제(데이터 포함) | `docker compose -f docker-compose.prod.yml down -v` ⚠️ |

- **데이터 영속**: MySQL(`mysql-data`), 사진(`uploads-data`), 인증서(`caddy-data`)가 named volume 에 남아 재배포해도 유지됩니다.
- **메모리 부족으로 빌드 실패 시**: swap 추가 — `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`
- **빌드를 VM에서 하기 싫으면**: 로컬/CI에서 이미지를 빌드해 레지스트리에 push 후, compose의 `build: .` 를 `image: <레지스트리/이미지:태그>` 로 바꾸면 됩니다.
- **백업**: `docker exec stp-mysql mysqldump -uroot -p$DB_PASSWORD smart_travel > backup.sql`
