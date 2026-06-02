# 백엔드 개발 실행 헬퍼.
#  1) 남은 앱 JVM 정리 (Ctrl+C 후 자식 JVM이 8082를 무는 문제 회피)
#  2) .env 로드 → 환경변수 주입
#  3) 채워진 소셜 자격증명에 따라 google/kakao 프로필 자동 활성화
#  4) MySQL(docker) 기동 후 백엔드 실행 → http://localhost:8082
#
# 사용법:  .\dev.ps1

$ErrorActionPreference = 'SilentlyContinue'

Write-Host "[1/4] 남은 SmartTravelPlanner JVM 정리..." -ForegroundColor Cyan
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'SmartTravelPlannerApplication' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host "    - killed PID $($_.ProcessId)" }

Write-Host "[2/4] .env 로드..." -ForegroundColor Cyan
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $idx = $line.IndexOf('=')
            $k = $line.Substring(0, $idx).Trim()
            $v = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
            if ($k) { Set-Item -Path "env:$k" -Value $v }
        }
    }
    Write-Host "    - .env 적용 완료"
} else {
    Write-Host "    - .env 없음 (.env.example 참고). 소셜 로그인은 비활성."
}

Write-Host "[3/4] 소셜 로그인 프로필 결정..." -ForegroundColor Cyan
$profiles = @()
if ($env:GOOGLE_CLIENT_ID -and $env:GOOGLE_CLIENT_SECRET) { $profiles += 'google' }
if ($env:KAKAO_CLIENT_ID  -and $env:KAKAO_CLIENT_SECRET)  { $profiles += 'kakao' }
if ($profiles.Count -gt 0) {
    $env:SPRING_PROFILES_ACTIVE = ($profiles -join ',')
    Write-Host "    - 활성 프로필: $($env:SPRING_PROFILES_ACTIVE) (소셜 로그인 ON)" -ForegroundColor Green
} else {
    Write-Host "    - 자격증명 없음 → 소셜 로그인 OFF (이메일 가입은 정상)"
}

Write-Host "[4/4] MySQL 기동 + 백엔드 실행 (http://localhost:8082)" -ForegroundColor Cyan
docker compose up -d | Out-Null

$ErrorActionPreference = 'Continue'
# --no-daemon: 방금 주입한 환경변수가 forked JVM 까지 확실히 전달되도록 (데몬 캐시 회피)
if ($profiles.Count -gt 0) {
    .\gradlew.bat bootRun --console=plain --no-daemon
} else {
    .\gradlew.bat bootRun --console=plain
}
