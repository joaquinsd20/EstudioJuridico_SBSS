<#
.SYNOPSIS
    LEXIA - Arranca todo de una sola vez: MySQL + backend + frontend + tunel.

.DESCRIPTION
    Orquestador del entorno de desarrollo:
      1. MySQL 8.0 (portable, sin servicio de Windows) en 127.0.0.1:3306
      2. Backend Spring Boot (mvnw spring-boot:run) en localhost:8080
      3. Frontend Angular (npm start) en localhost:4200
      4. Tunel de Cloudflare -> imprime la URL publica y el QR para el celular

    Cada paso arranca SOLO si su puerto no esta ya escuchando, asi que se puede
    re-ejecutar cuantas veces se quiera sin duplicar procesos.
    Los logs quedan en %TEMP%\lexia-logs\.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\levantar-lexia.ps1

.EXAMPLE
    .\scripts\levantar-lexia.ps1 -SinQR     # sin generar el QR en pantalla
#>
param(
    [switch]$SinQR
)

$ErrorActionPreference = 'Stop'
$raiz      = Split-Path -Parent $PSScriptRoot          # ...\EstudioJuridico_SBSS
$frontend  = Join-Path $raiz 'FrontendLexia'
$backend   = Join-Path $raiz 'BackendLexia'
$logs      = Join-Path $env:TEMP 'lexia-logs'
$mysqlBase = Join-Path $env:LOCALAPPDATA 'Programs\mysql'
$mysqlData = Join-Path $env:LOCALAPPDATA 'mysql-data'
$nodeDir   = Join-Path $env:LOCALAPPDATA 'Programs\nodejs'
$jdkDir    = Join-Path $env:LOCALAPPDATA 'Programs\jdk21'

New-Item -ItemType Directory -Force -Path $logs | Out-Null

# --- utilidades -------------------------------------------------------------
function puerto-Libre { param([int]$Puerto)
    -not (Get-NetTCPConnection -LocalPort $Puerto -State Listen -ErrorAction SilentlyContinue)
}
function esperar-Puerto { param([int]$Puerto, [int]$Segundos, [string]$Nombre)
    $t = 0
    while ($t -lt $Segundos) {
        if (-not (puerto-Libre $Puerto)) { return $true }
        Start-Sleep -Seconds 2; $t += 2
        Write-Host "  ... esperando $Nombre ($t s)" -ForegroundColor DarkGray
    }
    return $false
}
function fallo { param([string]$Mensaje)
    Write-Host "`nFALLO: $Mensaje" -ForegroundColor Red
    Write-Host "Logs en: $logs" -ForegroundColor DarkGray
    exit 1
}

Write-Host "`n=== LEXIA - arranque completo ===" -ForegroundColor Cyan
Write-Host "Raiz: $raiz`n" -ForegroundColor DarkGray

# --- entorno (rutas instaladas sin administrador) ---------------------------
if (Test-Path $nodeDir) { $env:PATH = "$nodeDir;$env:PATH" }
if (Test-Path $jdkDir)  { $env:PATH = "$jdkDir\bin;$env:PATH"; $env:JAVA_HOME = $jdkDir }

# --- 1) MySQL ---------------------------------------------------------------
Write-Host "[1/4] MySQL" -ForegroundColor Cyan
if (puerto-Libre 3306) {
    $mysqld = Join-Path $mysqlBase 'bin\mysqld.exe'
    if (-not (Test-Path $mysqld)) { fallo "No se encontro mysqld en $mysqld" }
    if (-not (Test-Path $mysqlData)) {
        Write-Host "  inicializando datadir (primera vez)..." -ForegroundColor DarkGray
        & $mysqld --initialize-insecure --basedir=$mysqlBase --datadir=$mysqlData
        if ($LASTEXITCODE -ne 0) { fallo "mysqld --initialize-insecure devolvio $LASTEXITCODE" }
    }
    $argMysqld = @(
        "--basedir=$mysqlBase", "--datadir=$mysqlData",
        "--bind-address=127.0.0.1", "--port=3306"
    )
    Start-Process -FilePath $mysqld -ArgumentList $argMysqld -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'mysqld.out.log') `
        -RedirectStandardError  (Join-Path $logs 'mysqld.err.log')
    if (-not (esperar-Puerto 3306 40 'MySQL')) { fallo "MySQL no abrio el 3306. Revisa mysqld.err.log" }
    Write-Host "  OK - MySQL en 127.0.0.1:3306" -ForegroundColor Green
} else {
    Write-Host "  OK - ya escuchando en 3306" -ForegroundColor Green
}

# --- 2) Backend -------------------------------------------------------------
Write-Host "[2/4] Backend Spring Boot" -ForegroundColor Cyan
if (puerto-Libre 8080) {
    if (-not (Test-Path (Join-Path $backend 'mvnw.cmd'))) { fallo "No se encontro mvnw.cmd en $backend" }
    Start-Process -FilePath (Join-Path $backend 'mvnw.cmd') -ArgumentList 'spring-boot:run' `
        -WorkingDirectory $backend -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'backend.log') `
        -RedirectStandardError  (Join-Path $logs 'backend.err.log')
    if (-not (esperar-Puerto 8080 180 'backend')) { fallo "El backend no abrio el 8080. Revisa backend.log" }
    Write-Host "  OK - backend en localhost:8080" -ForegroundColor Green
} else {
    Write-Host "  OK - ya escuchando en 8080" -ForegroundColor Green
}

# --- 3) Frontend ------------------------------------------------------------
Write-Host "[3/4] Frontend Angular" -ForegroundColor Cyan
if (puerto-Libre 4200) {
    $npm = Join-Path $nodeDir 'npm.cmd'
    if (-not (Test-Path $npm)) { fallo "No se encontro npm.cmd en $npm" }
    Start-Process -FilePath $npm -ArgumentList 'start' `
        -WorkingDirectory $frontend -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logs 'frontend.log') `
        -RedirectStandardError  (Join-Path $logs 'frontend.err.log')
    if (-not (esperar-Puerto 4200 180 'frontend')) { fallo "Angular no abrio el 4200. Revisa frontend.log" }
    Write-Host "  OK - frontend en localhost:4200" -ForegroundColor Green
} else {
    Write-Host "  OK - ya escuchando en 4200" -ForegroundColor Green
}

# --- 4) Tunel (bloquea aqui; Ctrl+C corta solo el tunel) --------------------
Write-Host "[4/4] Tunel de Cloudflare" -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'ver-en-celular.ps1') -SinQR:$SinQR
