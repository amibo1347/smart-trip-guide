# 백엔드 개발 실행 헬퍼.
# gradlew bootRun 을 Ctrl+C 로 끊으면 자식 JVM이 8082를 물고 남는 문제가 잦아,
# 시작 전에 남은 앱 JVM을 자동 정리한 뒤 기동한다.
#
# 사용법:  .\dev.ps1

$ErrorActionPreference = 'SilentlyContinue'

Write-Host "[1/3] 이전에 남은 SmartTravelPlanner JVM 정리..." -ForegroundColor Cyan
$leftover = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'SmartTravelPlannerApplication' }
foreach ($p in $leftover) {
    Stop-Process -Id $p.ProcessId -Force
    Write-Host "    - killed PID $($p.ProcessId)"
}
if (-not $leftover) { Write-Host "    - 정리할 프로세스 없음" }

Write-Host "[2/3] MySQL(docker) 기동 확인..." -ForegroundColor Cyan
docker compose up -d | Out-Null

Write-Host "[3/3] 백엔드 기동 (http://localhost:8082)" -ForegroundColor Cyan
$ErrorActionPreference = 'Continue'
.\gradlew.bat bootRun --console=plain
