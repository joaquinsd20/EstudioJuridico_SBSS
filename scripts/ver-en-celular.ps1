<#
.SYNOPSIS
    LEXIA - Ver el proyecto en el celular desde cualquier lugar (tunel de Cloudflare).

.DESCRIPTION
    No abre puertos en el router ni configura CORS: el celular entra por una URL
    publica https, el dev-server de Angular sirve la app y su proxy (proxy.conf.json)
    reenvia /api/** al backend en localhost:8080. El backend queda oculto.

    Requisitos (dos terminales):
      Terminal 1:  cd FrontendLexia; npm start
      Terminal 2:  backend (mvnw spring-boot:run) + este script

.PARAMETER Puerto
    Puerto del dev-server de Angular. Por defecto 4200 (el de 'npm start').

.PARAMETER SinQR
    No abre la pagina del codigo QR en el navegador del PC.

.PARAMETER ForzarDescarga
    Vuelve a descargar cloudflared aunque ya este en el equipo.

.EXAMPLE
    .\scripts\ver-en-celular.ps1

.NOTES
    La URL es efimera: cada vez que se cierra y abre el tunel cambia.
    Ctrl+C cierra el tunel. Si PowerShell bloquea el script:
        powershell -ExecutionPolicy Bypass -File scripts\ver-en-celular.ps1
#>
param(
    [int]$Puerto = 4200,
    [switch]$SinQR,
    [switch]$ForzarDescarga
)

$ErrorActionPreference = 'Stop'
$destino = "http://localhost:$Puerto"

# ---------------------------------------------------------------------------
# 1) ¿Hay algo escuchando en el puerto del frontend?
# ---------------------------------------------------------------------------
if (-not (Get-NetTCPConnection -LocalPort $Puerto -State Listen -ErrorAction SilentlyContinue)) {
    Write-Host ""
    Write-Host "  No hay nada escuchando en el puerto $Puerto." -ForegroundColor Red
    Write-Host "  Arranca el frontend en OTRA terminal:" -ForegroundColor Yellow
    Write-Host "      cd FrontendLexia" -ForegroundColor Yellow
    Write-Host "      npm start" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}
Write-Host "  Frontend  : puerto $Puerto OK (dev-server de Angular)" -ForegroundColor Green

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "  Backend   : puerto 8080 OK" -ForegroundColor Green
} else {
    Write-Host "  Backend   : puerto 8080 SIN escuchar. El frontal carga, pero el" -ForegroundColor Yellow
    Write-Host "              login fallara hasta que arranques 'mvnw spring-boot:run'." -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# 2) ¿cloudflared disponible? Si no, descarga el portable en LOCALAPPDATA.
# ---------------------------------------------------------------------------
$cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
if ($cmd) {
    $ruta = $cmd.Source
} else {
    $bin  = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'cloudflared-windows-arm64.exe' } else { 'cloudflared-windows-amd64.exe' }
    $ruta = Join-Path $env:LOCALAPPDATA "cloudflared\$bin"
    if ($ForzarDescarga -or -not (Test-Path $ruta)) {
        $urlDescarga = "https://github.com/cloudflare/cloudflared/releases/latest/download/$bin"
        Write-Host "  Descargando cloudflared ($urlDescarga)..."
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        New-Item -ItemType Directory -Force -Path (Split-Path $ruta) | Out-Null
        Invoke-WebRequest -Uri $urlDescarga -OutFile $ruta -UseBasicParsing
    }
}

# ---------------------------------------------------------------------------
# 3) Abrir el tunel y capturar la URL publica.
# ---------------------------------------------------------------------------
$salida = Join-Path $env:TEMP "lexia-tunel-salida.log"
$error_ = Join-Path $env:TEMP "lexia-tunel-error.log"
Remove-Item $salida, $error_ -ErrorAction SilentlyContinue

Write-Host "  Abriendo tunel a $destino ..."
$proc = Start-Process -FilePath $ruta -ArgumentList @('tunnel', '--url', $destino) -PassThru `
    -WindowStyle Hidden -RedirectStandardOutput $salida -RedirectStandardError $error_

$publica = $null
for ($i = 0; $i -lt 60 -and -not $publica; $i++) {
    Start-Sleep -Milliseconds 500
    if ($proc.HasExited) { break }
    $coincide = Select-String -Path $salida, $error_ -Pattern 'https://\S+\.trycloudflare\.com' `
        -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($coincide) { $publica = $coincide.Matches[0].Value }
}

if (-not $publica) {
    Write-Host ""
    Write-Host "  No salio la URL del tunel. Ultima salida de cloudflared:" -ForegroundColor Red
    if (Test-Path $error_) { Get-Content $error_ -Tail 30 | ForEach-Object { Write-Host "    $_" } }
    if (Test-Path $salida) { Get-Content $salida -Tail 30 | ForEach-Object { Write-Host "    $_" } }
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
    exit 1
}

Write-Host ""
Write-Host "  +-------------------------------+" -ForegroundColor Cyan
Write-Host "  |  ABRE ESTO EN EL CELULAR      |" -ForegroundColor Cyan
Write-Host "  +-------------------------------+" -ForegroundColor Cyan
Write-Host "  $publica" -ForegroundColor Green
Write-Host ""
Write-Host "  Ctrl+C para cerrar el tunel (la URL cambia en cada arranque)."

if (-not $SinQR) {
    $qr = "https://api.qrserver.com/v1/create-qr-code/?size=320x320&data=$([uri]::EscapeDataString($publica))"
    Start-Process $qr
    Write-Host "  Se abrio el QR en el navegador: escanealo con la camara del celular."
}

try {
    Wait-Process -Id $proc.Id
} finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
}
