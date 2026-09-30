# preflight.ps1 - ECOS dev environment self-check (no side effects beyond reads).
# Usage:
#   .\preflight.ps1                  # full check incl. docker infra
#   .\preflight.ps1 -SkipDocker
# Exit code 0 = all required checks pass.
param(
    [string[]]$Modules = @('gateway'),
    [switch]$SkipDocker,
    [switch]$SkipJars
)
$ErrorActionPreference = 'Continue'
$RepoRoot   = Split-Path -Parent $PSScriptRoot
$Backend    = Join-Path $RepoRoot 'ecos_backend'
$MvnCmd     = 'D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd'
$JavaExe    = 'C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot\bin\java.exe'
$JwtPemFile = Join-Path $env:USERPROFILE '.config\ecos\jwt-private-key.pem'
$HermesEnv  = Join-Path $env:USERPROFILE '.hermes\profiles\gorunkol\.env'
$fail = 0
function Step([bool]$ok, [string]$name, [string]$detail) {
    $tag = if ($ok) { '[PASS]' } else { '[FAIL]' }
    Write-Host "$tag $name $(if ($detail) { "- $detail" })"
    if (-not $ok) { $script:fail++ }
}

Step (Test-Path $JavaExe) 'JDK 17' $JavaExe
Step (Test-Path $MvnCmd)  'Maven 3.9.11' $MvnCmd
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
Step ($null -ne $nodeCmd) 'node on PATH' $(if ($nodeCmd) { (node -v) 2>$null } else { 'install Node.js or add to PATH' })

Step (Test-Path $JwtPemFile) 'JWT private key' $JwtPemFile
# DEEPSEEK: env var wins; otherwise first key file found (~\.config\ecos\.env, legacy ~\.hermes\...). Not fatal: AI features degrade, platform still starts.
$EnvFiles = @((Join-Path $env:USERPROFILE '.config\ecos\.env'), $HermesEnv)
$deepSource = if ($env:DEEPSEEK_API_KEY) { 'process env DEEPSEEK_API_KEY' } else { $EnvFiles | Where-Object { (Test-Path $_) -and ((Get-Content $_ -ErrorAction SilentlyContinue) -match 'DEEPSEEK_API_KEY') } | Select-Object -First 1 }
if ($deepSource) { Step $true 'DEEPSEEK_API_KEY available' $deepSource }
else { Write-Host '[WARN] DEEPSEEK_API_KEY not found (checked process env + ' ($EnvFiles -join ' / ') ') - AI features will be disabled' }

# PMO-74 H10-T4 credential externalization: infra password defaults no longer live in repo yml
# (yml keeps only "${VAR:}" empty defaults). Missing injection = startup failure, so these are
# mandatory items (counted as FAIL). Source priority: process env, then ~/.config/ecos/.env.
# NOTE: comments here stay ASCII-only - Windows PowerShell 5.1 reads this UTF-8-no-BOM file as
# GBK, and multi-byte Chinese bytes can swallow the following token, breaking the parse.
foreach ($credKey in @('DB_PASSWORD', 'NEO4J_PASSWORD', 'MINIO_SECRET_KEY')) {
    $envVal = (Get-Item -Path "Env:$credKey" -ErrorAction SilentlyContinue).Value
    if ($envVal) {
        $credSrc = "process env $credKey"
    } else {
        $credSrc = $EnvFiles | Where-Object { (Test-Path $_) -and ((Get-Content $_ -ErrorAction SilentlyContinue) -match "^\s*$credKey\s*=") } | Select-Object -First 1
    }
    $hint = "define $credKey in $($EnvFiles[0]) (dev container defaults, see ecos-docker/docker-compose.yml)"
    Step ([bool]$credSrc) "infra credential $credKey" $(if ($credSrc) { $credSrc } else { $hint })
}

if (-not $SkipDocker) {
    $dockerOk = $false
    try { docker version --format '{{.Server.Version}}' *> $null; $dockerOk = ($LASTEXITCODE -eq 0) } catch {}
    Step $dockerOk 'Docker daemon reachable' 'Docker Desktop'
    if ($dockerOk) {
        $running = (docker ps --filter 'name=ecos-' --format '{{.Names}}') -split "`n" | Where-Object { $_ }
        $expected = @('ecos-postgres','ecos-neo4j','ecos-minio','ecos-opa','ecos-kafka','ecos-zookeeper')
        $missing = $expected | Where-Object { $running -notcontains $_ }
        Step ($missing.Count -eq 0) 'Infra containers running (6x ecos-*)' $(if ($missing) { "missing: $($missing -join ',')" })
        if ($running -contains 'ecos-postgres') {
            $pg = docker exec ecos-postgres psql -U postgres -d sys_man -tAc 'SELECT 1' 2>$null
            Step ($pg -match '1') 'PostgreSQL sys_man reachable' ''
        }
    }
}

$PortMap = @{ gateway=8080; sysman=18081; datanet=18082; buszhi=18083; aiming=18084; dccheng=18086; workspace=18090 }
foreach ($m in $Modules) {
    if ($PortMap.ContainsKey($m)) {
        $busy = Get-NetTCPConnection -LocalPort $PortMap[$m] -State Listen -ErrorAction SilentlyContinue
        Step (-not $busy) "Port $($PortMap[$m]) free ($m)" $(if ($busy) { "PID $($busy[0].OwningProcess) - run stop-backend.ps1 first" })
    }
}

if (-not $SkipJars) {
    foreach ($m in $Modules) {
        if (-not $PortMap.ContainsKey($m)) { continue }
        $jar = switch ($m) {
            'gateway'   { "$Backend\gateway\target\gateway-1.0.0-SNAPSHOT.jar" }
            'workspace' { "$Backend\workspace\workspace-service\target\workspace-service-1.0.0-SNAPSHOT.jar" }
            default     { "$Backend\services\$m\target\$m-service-1.0.0-SNAPSHOT.jar" }
        }
        Step (Test-Path $jar) "Jar built ($m)" $(if (-not (Test-Path $jar)) { "run: & `"$MvnCmd`" -f `"$Backend\pom.xml`" clean install -DskipTests" })
    }
}

if ($fail -eq 0) { Write-Host "`nPREFLIGHT OK" } else { Write-Host "`nPREFLIGHT FAILED ($fail issue(s))"; exit 1 }
