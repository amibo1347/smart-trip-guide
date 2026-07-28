# Oracle Cloud AMD Micro(1GB) 배포 가이드 — 로컬 빌드 + GHCR

A1(ARM) 용량이 만석일 때, **VM.Standard.E2.1.Micro (AMD, 1 OCPU / 1GB)** 로 배포하는 방법입니다.
1GB 라 VM 에서 직접 빌드가 불가능하므로, **내 PC 에서 이미지를 빌드해 GHCR 에 올리고 VM 은 받아서 실행만** 합니다.

> 핵심 파일: `Dockerfile`, `docker-compose.micro.yml`, `Caddyfile`, `.env.prod.example`
> 이미지: `ghcr.io/amibo1347/smart-trip-guide:latest`

---

## A. 내 PC 에서 (이미지 빌드 → GHCR push) — 코드 바뀔 때마다 반복

### A-1. GitHub PAT(토큰) 발급 (최초 1회)
GitHub → Settings → Developer settings → **Personal access tokens (classic)** → Generate
- 권한(scope): **`write:packages`** (+ 자동 포함되는 `read:packages`)
- 발급된 토큰 문자열을 복사(`ghp_...`)

### A-2. GHCR 로그인 (PowerShell)
```powershell
$env:GHCR_PAT = "ghp_여기에_토큰"
$env:GHCR_PAT | docker login ghcr.io -u amibo1347 --password-stdin
```

### A-3. amd64 로 빌드 후 push (프로젝트 루트에서)
```powershell
docker build --platform linux/amd64 -t ghcr.io/amibo1347/smart-trip-guide:latest .
docker push ghcr.io/amibo1347/smart-trip-guide:latest
```
> 내 PC 가 x86 이라 `linux/amd64` 는 네이티브 빌드 — 빠릅니다. 첫 빌드는 프론트(npm)+백엔드(gradle) 때문에 수 분.

### A-4. (권장) 패키지를 public 으로
GitHub → 본인 프로필 → **Packages → smart-trip-guide → Package settings → Change visibility → Public**.
→ 그러면 **VM 에서 docker login 없이** 바로 pull 가능. (private 로 두려면 B-5 에서 VM 로그인 필요)

---

## B. VM 에서 (기존 AMD 인스턴스 재사용)

### B-1. 포트 열기 (Oracle 방화벽 2겹 — 둘 다 필수)
**(A) 클라우드** — VCN → Subnet → **Security List → Ingress Rules**: TCP **80**, TCP **443** (0.0.0.0/0)
**(B) VM OS** — SSH 접속 후:
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

### B-2. 도메인 연결 (DuckDNS)
DuckDNS 서브도메인의 **current ip** 에 VM Public IP 입력 → update.

### B-3. Docker 설치
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

### B-4. swap 4G 추가 (1GB VM 필수! — 안 하면 런타임 OOM)
```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h     # Swap 줄에 4.0Gi 보이면 OK
```

### B-5. compose/Caddyfile/.env 준비
```bash
git clone https://github.com/amibo1347/smart-trip-guide.git
cd smart-trip-guide
git checkout all
cp .env.prod.example .env
nano .env     # APP_DOMAIN, DB_PASSWORD, GEMINI_API_KEY 채우기
```
> 패키지를 **private** 로 뒀다면 여기서 GHCR 로그인(read 권한 PAT):
> `echo <READ_PAT> | docker login ghcr.io -u amibo1347 --password-stdin`

### B-6. 실행 (빌드 X — pull 후 바로 기동)
```bash
docker compose -f docker-compose.micro.yml up -d
docker compose -f docker-compose.micro.yml logs -f app     # Started ... 보이면 완료
docker compose -f docker-compose.micro.yml logs -f caddy   # 인증서 발급(수십 초)
```

### B-7. 접속
폰/PC 브라우저 → **`https://<도메인>`** → (폰) 공유 → 홈 화면에 추가(PWA).

---

## 코드 업데이트 후 재배포
```powershell
# 내 PC
docker build --platform linux/amd64 -t ghcr.io/amibo1347/smart-trip-guide:latest .
docker push ghcr.io/amibo1347/smart-trip-guide:latest
```
```bash
# VM
docker compose -f docker-compose.micro.yml pull app
docker compose -f docker-compose.micro.yml up -d
```

## 운영 팁 / 1GB 메모리 주의
| 작업 | 명령 |
|---|---|
| 상태 | `docker compose -f docker-compose.micro.yml ps` |
| 메모리 | `free -h` / `docker stats` |
| 로그 | `docker compose -f docker-compose.micro.yml logs -f app` |

- **메모리 배분(목표)**: MySQL ~250MB + 앱 JVM 힙 256MB + Caddy ~40MB + OS → swap 으로 스파이크 흡수.
- 앱이 자꾸 죽으면(`docker ps` 에서 Restarting): `JAVA_TOOL_OPTIONS` 를 `-Xmx200m` 으로 더 낮추거나 swap 을 6G 로.
- MySQL 이 무거우면 `--innodb-buffer-pool-size` 를 32M 로.
- **데이터 영속**: `mysql-data`, `uploads-data`, `caddy-data` named volume 에 유지.
- **백업**: `docker exec stp-mysql mysqldump -uroot -p$DB_PASSWORD smart_travel > backup.sql`
