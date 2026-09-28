# start-frontend.ps1 - ECOS frontend dev entry (Express BFF + vite on :3000).
param([switch]$Build)
$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$Fe = Join-Path $RepoRoot 'ecos_frontend'
Set-Location $Fe
if (-not (Test-Path (Join-Path $Fe 'node_modules'))) {
    Write-Host '[INFO] node_modules missing; running npm install'
    npm install
}
$busy = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
if ($busy) { Write-Host "[FAIL] port 3000 already in use by PID $($busy[0].OwningProcess)"; exit 1 }
if ($Build) { npm run build } else { npm run dev }
