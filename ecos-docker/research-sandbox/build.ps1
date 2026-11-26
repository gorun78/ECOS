# Build the L3 research sandbox image (2026-11-25)
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
#
# Why precompile with javac instead of a multi-stage docker build:
#   Docker Hub is unreachable from this machine (registry-1.docker.io /
#   auth.docker.io both return 000), so the image cannot pull a maven or
#   python stage to compile inside. We compile on the host and COPY only
#   the classes - which also means the image ships no compiler at all.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 as
#   ANSI (GBK here), and non-ASCII bytes break the parser.
#
# Prereq: JDK 17 on the host (JAVA_HOME -> PATH, same order as _win_tasks).

param(
    [string]$Tag = 'ecos-research-sandbox:1.1.1'
)
$ErrorActionPreference = 'Stop'

# $PSScriptRoot can be empty under -File on PowerShell 5.1; fall back to MyInvocation.
$here = $PSScriptRoot
if (-not $here) {
    $here = Split-Path -Parent $MyInvocation.MyCommand.Definition
}
if (-not $here) {
    throw 'Cannot resolve script directory; invoke with -File'
}

$javac = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
} else {
    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { $javac = $cmd.Source }
}
if (-not $javac) {
    throw 'javac not found: set JAVA_HOME or put JDK 17 on PATH'
}

$outDir = Join-Path $here 'out'
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Path $outDir | Out-Null

$sources = Get-ChildItem -Path (Join-Path $here 'src') -Filter *.java -Recurse | ForEach-Object { $_.FullName }
Write-Host "[INFO] javac $($sources.Count) source file(s) -> $outDir"
& $javac -encoding UTF-8 -d $outDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Write-Host "[INFO] docker build -t $Tag"
& docker build -t $Tag -f (Join-Path $here 'Dockerfile') $here
if ($LASTEXITCODE -ne 0) { throw "docker build failed with exit code $LASTEXITCODE" }

Write-Host "[OK] image built: $Tag"
Write-Host "     start:  docker compose -f ..\docker-compose.yml up -d research-sandbox"
Write-Host "     verify: curl http://127.0.0.1:18099/health"
