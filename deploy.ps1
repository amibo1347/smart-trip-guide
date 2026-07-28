# =====================================================================
# 오라클 서버(oracle-server) 재배포 — 로컬에서 jar 를 빌드해 올리고 서비스만 재시작.
# 서버는 systemd + nginx 방식이라 Docker 를 쓰지 않는다(같은 VM 에 team/musiclv/loa 동거).
#
#   .\deploy.ps1              # 빌드 → 업로드 → 재시작 → 로그 확인
#   .\deploy.ps1 -SkipBuild   # 이미 빌드된 jar 를 그대로 올린다
#
# 사전 조건: ~/.ssh/config 에 Host oracle-server 정의 (HostName/User/IdentityFile)
# 자세한 구성: DEPLOY-server.md
# =====================================================================
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$Remote = 'oracle-server'
$Jar    = 'build/libs/smart-travel-planner-0.0.1-SNAPSHOT.jar'

Set-Location $PSScriptRoot

if (-not $SkipBuild) {
    # 로컬에서 앱이 jar 로 떠 있으면 파일이 잠겨 clean 이 실패한다 — 먼저 내린다.
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -like '*smart-travel-planner-0.0.1-SNAPSHOT.jar*' } |
        ForEach-Object {
            Write-Host "실행 중인 로컬 jar 인스턴스 종료: PID $($_.ProcessId)" -ForegroundColor Yellow
            Stop-Process -Id $_.ProcessId -Force
        }

    Write-Host '=> 빌드 (프론트 + 백엔드)' -ForegroundColor Cyan
    & .\gradlew.bat clean bootJar --no-daemon -x test
    if ($LASTEXITCODE -ne 0) { throw '빌드 실패' }
}

if (-not (Test-Path $Jar)) { throw "jar 가 없습니다: $Jar" }
Write-Host "=> 업로드 ($([math]::Round((Get-Item $Jar).Length / 1MB)) MB)" -ForegroundColor Cyan

# 업로드 중 서비스가 반쯤 쓰인 jar 를 잡지 않도록 임시 파일로 올린 뒤 교체한다.
scp $Jar "${Remote}:/opt/planner/planner.jar.new"
if ($LASTEXITCODE -ne 0) { throw '업로드 실패' }

Write-Host '=> 서비스 재시작' -ForegroundColor Cyan
ssh $Remote 'mv /opt/planner/planner.jar.new /opt/planner/planner.jar && sudo systemctl restart planner && sleep 20 && systemctl is-active planner'
if ($LASTEXITCODE -ne 0) { throw '재시작 실패 — ssh oracle-server "sudo journalctl -u planner -n 100" 로 확인' }

Write-Host '=> 상태 확인' -ForegroundColor Cyan
ssh $Remote 'curl -s -o /dev/null -w "health: HTTP %{http_code}\n" http://127.0.0.1:8082/api/health; sudo journalctl -u planner -n 5 --no-pager'

Write-Host '완료' -ForegroundColor Green
