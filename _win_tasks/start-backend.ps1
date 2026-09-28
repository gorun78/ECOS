# start-backend.ps1 - ECOS microservice v2 launcher (Windows native)
# Replaces the lost start-gateway.ps1 (never committed to git). See agents.md port table.
# Usage:
#   .\start-backend.ps1                              # gateway only
#   .\start-backend.ps1 -Modules gateway,sysman,datanet,buszhi,aiming,dccheng,workspace
#   .\start-backend.ps1 -Modules kb-boot             # engine boot debug, auto port offset 18086->19086
#   .\start-backend.ps1 -Modules kb-boot -ForcePort 18086   # explicit override
param(
    [string[]]$Modules = @('gateway'),
    [string]$Profile = 'standard',
    [int[]]$ForcePort = @(),
    [switch]$NoEnv
)
$ErrorActionPreference = 'Stop'
$RepoRoot   = Split-Path -Parent $PSScriptRoot
$Backend    = Join-Path $RepoRoot 'ecos_backend'
$JavaExe    = 'C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot\bin\java.exe'
$MvnCmd     = 'D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd'
$LogDir     = Join-Path $PSScriptRoot 'logs'
$JwtPemFile = Join-Path $env:USERPROFILE '.config\ecos\jwt-private-key.pem'
$HermesEnv  = Join-Path $env:USERPROFILE '.hermes\profiles\gorunkol\.env'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# name -> base port + jar path. Services/gateway/workspace are the 7 production JARs;
# *-boot entries are engine standalone debug only (non-production, per architecture rules 0.3).
$Map = [ordered]@{
    'gateway'   = @{ Port = 8080;  Jar = "$Backend\gateway\target\gateway-1.0.0-SNAPSHOT.jar" }
    'sysman'    = @{ Port = 18081; Jar = "$Backend\services\sysman\target\sysman-service-1.0.0-SNAPSHOT.jar" }
    'datanet'   = @{ Port = 18082; Jar = "$Backend\services\datanet\target\datanet-service-1.0.0-SNAPSHOT.jar" }
    'buszhi'    = @{ Port = 18083; Jar = "$Backend\services\buszhi\target\buszhi-service-1.0.0-SNAPSHOT.jar" }
    'aiming'    = @{ Port = 18084; Jar = "$Backend\services\aiming\target\aiming-service-1.0.0-SNAPSHOT.jar" }
    'dccheng'   = @{ Port = 18086; Jar = "$Backend\services\dccheng\target\dccheng-service-1.0.0-SNAPSHOT.jar" }
    'workspace' = @{ Port = 18090; Jar = "$Backend\workspace\workspace-service\target\workspace-service-1.0.0-SNAPSHOT.jar" }
    'security-boot'   = @{ Port = 18081; Jar = "$Backend\engine\security-engine\security-engine-boot\target\security-engine-boot-1.0.0-SNAPSHOT-exec.jar" }
    'data-boot'       = @{ Port = 18082; Jar = "$Backend\engine\data-engine\data-engine-boot\target\data-engine-boot-1.0.0-SNAPSHOT-exec.jar" }
    'ontology-boot'   = @{ Port = 18083; Jar = "$Backend\engine\ontology-engine\ontology-engine-boot\target\ontology-engine-boot-1.0.0-SNAPSHOT-exec.jar" }
    'kb-boot'         = @{ Port = 18086; Jar = "$Backend\engine\kb-engine\kb-engine-boot\target\kb-engine-boot-1.0.0-SNAPSHOT-exec.jar" }
    'cognitive-boot'  = @{ Port = 18089; Jar = "$Backend\engine\cognitive-engine\cognitive-engine-boot\target\cognitive-engine-boot-1.0.0-SNAPSHOT-exec.jar" }
    'ai-boot'         = @{ Port = 18084; Jar = "$Backend\engine\ai-engine\ai-engine-boot\target\ai-engine-boot-1.0.0-SNAPSHOT-exec.jar" }
}

function Import-EnvFile([string]$Path) {
    if (-not (Test-Path $Path)) { Write-Host "[WARN] env file missing: $Path"; return }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') { Set-Item -Path "Env:\$($matches[1])" -Value $matches[2].Trim() }
    }
}

if (-not $NoEnv) {
    if (Test-Path $JwtPemFile) {
        # gateway/sysman read JWT_PRIVATE_KEY with literal \n as line separator (application.yml SEC-P0-2)
        $pem = (Get-Content $JwtPemFile -Raw).Trim("`r", "`n") -replace "`r", ''
        $env:JWT_PRIVATE_KEY = ($pem -split "`n") -join '\n'
        Write-Host "[OK] JWT_PRIVATE_KEY loaded ($($env:JWT_PRIVATE_KEY.Length) chars)"
    } else {
        Write-Host "[WARN] JWT pem missing: $JwtPemFile - auth will fail"
    }
    # DEEPSEEK etc.: later files win; missing files are skipped silently by design
    Import-EnvFile (Join-Path $env:USERPROFILE '.config\ecos\.env')   # 本机密钥落点（.hermes 已不存在，2026-09-28 实证）
    Import-EnvFile $HermesEnv                                          # 历史路径，存在则兼容
    Import-EnvFile (Join-Path $RepoRoot '.env')
}

foreach ($m in $Modules) {
    if (-not $Map.Contains($m)) { Write-Host "[FAIL] unknown module '$m'; known: $($Map.Keys -join ',')"; exit 1 }
    $e = $Map[$m]
    $port = $e.Port
    if ($m.EndsWith('-boot')) { $port = $port + 1000 }   # avoid clash with the aggregating service
    if ($ForcePort.Count -gt 0) { $port = $ForcePort[0]; $ForcePort = @($ForcePort | Select-Object -Skip 1) }
    if (-not (Test-Path $e.Jar)) {
        Write-Host "[FAIL] jar missing: $($e.Jar)"
        Write-Host "       build first: & `"$MvnCmd`" -f `"$Backend\pom.xml`" clean install -DskipTests"
        exit 1
    }
    $listeners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($l in $listeners) {
        Write-Host "[INFO] port $port occupied by PID $($l.OwningProcess); stopping old instance of $m"
        Stop-Process -Id $l.OwningProcess -Force
        Start-Sleep -Seconds 2
    }
    $log = Join-Path $LogDir "$m-stdout.log"
    Start-Process -FilePath $JavaExe `
        -ArgumentList @('-Xms128m', '-Xmx768m', '-jar', $e.Jar, "--spring.profiles.active=$Profile", "--server.port=$port") `
        -RedirectStandardOutput $log -RedirectStandardError (Join-Path $LogDir "$m-stderr.log") -WindowStyle Hidden
    $ok = $false
    foreach ($i in 1..30) {
        Start-Sleep -Seconds 2
        if (Test-NetConnection -ComputerName localhost -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue) { $ok = $true; break }
    }
    if ($ok) { Write-Host "[OK] $m listening on :$port (log: $log)" }
    else { Write-Host "[FAIL] $m not up on :$port within 60s - check $log" }
}
