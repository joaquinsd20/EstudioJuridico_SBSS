# Arranca el backend con las credenciales de .env.local
#
#   .\Arrancar.ps1
#
# El .env.local no se versiona, asi que la contrasena no queda en el repo.

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $raiz '.env.local'

if (-not (Test-Path -LiteralPath $envFile)) {
  Write-Host "Falta .env.local" -ForegroundColor Red
  Write-Host "  Copy-Item .env.ejemplo .env.local   y rellena DB_PASSWORD."
  exit 1
}

# Carga el .env.local como variables de entorno del proceso actual.
foreach ($linea in Get-Content -LiteralPath $envFile) {
  if ($linea -match '^\s*#' -or $linea -notmatch '=') { continue }
  $nombre, $valor = $linea -split '=', 2
  Set-Item -Path "Env:$($nombre.Trim())" -Value $valor.Trim()
}

$env:SPRING_PROFILES_ACTIVE = 'dev,local'

Write-Host "Base de datos : $env:DB_HOST`:$env:DB_PORT/$env:DB_NAME  como $env:DB_USERNAME" -ForegroundColor DarkGray
Write-Host "Perfil        : $env:SPRING_PROFILES_ACTIVE" -ForegroundColor DarkGray
Write-Host ""

& (Join-Path $raiz 'mvnw.cmd') spring-boot:run