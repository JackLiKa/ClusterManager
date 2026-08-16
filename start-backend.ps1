# MQCluster Backend Startup Script
# Solves: terminal window inherits old JAVA_HOME (Java 17), causing UnsupportedClassVersionError
# Usage: run from project root  .\start-backend.ps1

$ErrorActionPreference = "Stop"

$javaHome = "C:\Users\13026\.jdks\ms-21.0.9"

if (-not (Test-Path $javaHome)) {
    Write-Error "Java 21 not found at: $javaHome"
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

Write-Host "Using Java:" -ForegroundColor Cyan
& "$javaHome\bin\java.exe" -version
Write-Host ""

# Kill process occupying port 8088
$old = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue
if ($old) {
    Write-Host "Port 8088 in use, cleaning up..." -ForegroundColor Yellow
    $old | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
    Start-Sleep -Seconds 2
}

# Clean old RocketMQ store (avoid store lock)
Set-Location "$PSScriptRoot\java"
if (Test-Path run) {
    Write-Host "Cleaning old run/ directory..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force run
}

Write-Host "Starting backend..." -ForegroundColor Green
.\mvnw.cmd spring-boot:run