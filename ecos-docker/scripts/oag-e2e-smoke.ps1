<#
.SYNOPSIS
    OAG E2E 冒烟测试脚本 — 验证 gateway :8080 的 /api/v1/oag/* 转发到 aiming :18084 路由通畅
.DESCRIPTION
    按服务器当前状态自适应执行 0-5 项检查，任一前置端口未起 → WARN + exits 2；
    其余 FAIL 项累计后输出 PASS/FAIL 总结。
.PARAMETER Port
    覆盖 gateway 端口（默认 8080）
.PARAMETER AimingPort
    覆盖 aiming 端口（默认 18084）
.PARAMETER Timeout
    单请求超时秒数（默认 10）
.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File oag-e2e-smoke.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File oag-e2e-smoke.ps1 -Port 8084 -Timeout 20
    powershell -NoProfile -ExecutionPolicy Bypass -File oag-e2e-smoke.ps1 -AimingPort 19084
#>
[CmdletBinding()]
param(
    [int]$Port = 8080,
    [int]$AimingPort = 18084,
    [int]$Timeout = 10
)

$ErrorActionPreference = 'Stop'

# ── 共用常量 ──────────────────────────────────────────────────────
$GATEWAY  = "http://localhost:$Port"
$AIMING   = "http://localhost:$AimingPort"
$TODAY    = Get-Date -Format 'yyyy-MM-dd'
$MAX_SSE_FRAMES = 2
$results  = New-Object System.Collections.Generic.List[object]   # 承载结果汇总

function Write-Result {
    param([string]$Id, [string]$Name, [string]$Http = '--', [long]$Ms = -1, [string]$Verdict)
    Write-Host ("  [{0}] {1,-22}  HTTP {2,-5}  {3,-7}  {4}" -f $Id, $Name, $Http, `
        $(if ($Ms -ge 0) { "$Ms ms" } else { '--' }), $Verdict)
}

function Get-HttpCode {
    param([string]$Url, [string]$Method = 'GET', [string]$DataFile = $null, [int]$Sec = $Timeout)
    # 用 System.Net.HttpWebRequest (纯 .NET, 不依赖外部 curl.exe — 本子 shell 会把
    # NUL 重定向改写为字面量, 导致误报 000)
    try {
        $req = [System.Net.HttpWebRequest]::Create($Url)
        $req.Method = $Method
        $req.Timeout = $Sec * 1000
        $req.ReadWriteTimeout = $Sec * 1000
        $req.AllowAutoRedirect = $false
        $req.UserAgent = 'oag-e2e-smoke/1.0'
        if ($Method -eq 'POST') {
            $req.ContentType = 'application/json'
            $bytes = @()
            if ($DataFile -ne $null -and (Test-Path $DataFile)) {
                $bytes = [System.IO.File]::ReadAllBytes($DataFile)
            }
            $req.ContentLength = $bytes.Length
            $stream = $req.GetRequestStream()
            if ($bytes.Length -gt 0) { $stream.Write($bytes, 0, $bytes.Length) }
            $stream.Close()
        }
        $resp = $req.GetResponse()
        $code = [string]([int]$resp.StatusCode)
        $resp.Close()
        return $code
    } catch [System.Net.WebException] {
        try {
            # 4xx/5xx 走 catch, 读 HttpWebResponse.StatusCode (int→3位字符串)
            $er = $_.Exception.Response
            if ($er -ne $null) {
                $code = [string]([int]$er.StatusCode)
                $er.Close()
                return $code
            }
            return '000'
        } catch { return '000' }
    } catch {
        Write-Verbose "HTTP 请求失败: $($_.Exception.Message)"
        return '000'
    }
}

function Read-SseFrames {
    param([string]$Url, [string]$DataFile, [int]$MaxFrames = 8, [int]$Sec = $Timeout)
    # curl.exe --no-buffer + --max-time 让 chunked SSE 立即 flush 拿 N 帧
    $frames = @()
    $tmpResp = Join-Path $env:TEMP ("oag-sse-" + [guid]::NewGuid().ToString('N') + ".txt")
    try {
        if (-not (Test-Path $DataFile)) { return $frames }
        & curl.exe -s --no-buffer --max-time $Sec -X POST $Url `
            -H "Content-Type: application/json" `
            -H "Accept: text/event-stream" `
            --data-binary "@$DataFile" `
            -o $tmpResp 2>$null | Out-Null
        $raw = (Get-Content $tmpResp -Raw -ErrorAction SilentlyContinue -Encoding UTF8) -replace "`r`n", "`n"
        if (-not $raw) { return $frames }
        foreach ($line in ($raw -split "`n")) {
            if ($line -match '^event:\s*(\S+)') {
                $frames += @{ kind = 'event'; text = $Matches[1] }
            } elseif ($line -match '^data:\s*(.{0,120})') {
                $frames += @{ kind = 'data';  text = $Matches[1] }
            }
            if ($frames.Count -ge $MaxFrames) { break }
        }
    } finally {
        if (Test-Path $tmpResp) { Remove-Item $tmpResp -Force -ErrorAction SilentlyContinue }
    }
    return $frames
}

# ── 主流程 ───────────────────────────────────────────────────────
$totalStart = [Environment]::TickCount
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host " OAG E2E Smoke  ($TODAY)" -ForegroundColor Cyan
Write-Host " 目标: gateway=$GATEWAY   aiming=$AIMING" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════" ""

Write-Host " [PRE] 前置端口探测" -ForegroundColor Yellow

# [0] gateway /api/health
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$code0 = Get-HttpCode -Url "$GATEWAY/api/health" -Method 'GET'
$verdict = if ($code0 -eq '200') { 'PASS' } else { 'FAIL' }
Write-Result '0' '/api/health (gateway 自检)' $code0 $sw.ElapsedMilliseconds $verdict
$results.Add(@{ Id = 0; Name = '/api/health (gateway)'; Http = $code0; Verdict = $verdict })
$sw.Stop()

# [1] aiming /api/v1/oag/chat/health 直连
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$code1 = Get-HttpCode -Url "$AIMING/api/v1/oag/chat/health" -Method 'GET'
$sw.Stop()
$verdict = if ($code1 -eq 200) { 'PASS' } else { 'FAIL' }
Write-Result '1' 'GET /api/v1/oag/chat/health (aiming 直连)' $code1 $sw.ElapsedMilliseconds $verdict
$results.Add(@{ Id = 1; Name = 'GET /oag/chat/health (aiming)'; Http = $code1; Verdict = $verdict })
$sw.Stop()

# 任一前置未起 → 退出 2
if ($code0 -eq '000' -or $code1 -eq '000') {
    Write-Host ""
    Write-Host "  [WARN] gateway :$Port 与 aiming :$AimingPort 至少一个不可访问。`n" -ForegroundColor Yellow
    Write-Host "◄◄◄ [EXIT 2] 前置端口未起，无法完成 E2E 冒烟 — 请先启动 gateway 与 services/aiming" `
        -ForegroundColor Red
    exit 2
}

# ── 准备载荷（写临时文件，避免 PowerShell -d 内联 JSON 转义陷阱） ──
$guid   = [guid]::NewGuid().ToString('N').Substring(0, 12)
$tmpV2  = Join-Path $env:TEMP "oag-e2e-v2-$guid.json"
$tmpV1  = Join-Path $env:TEMP "oag-e2e-v1-$guid.json"
try {
    # 写入 UTF-8 无 BOM 的 JSON（避免 BOM 让后端 Jackson 解析 400）；ASCII 表达是为了与 [4] echo 同字段
    $bodyV2 = '{"message":"hello e2e smoke test v2","userId":"smoke_test","tenantId":"default"}'
    $bodyV1 = '{"message":"hi","userId":"smoke_test","tenantId":"default"}'
    [System.IO.File]::WriteAllText($tmpV2, $bodyV2, (New-Object System.Text.UTF8Encoding($false)))
    [System.IO.File]::WriteAllText($tmpV1, $bodyV1, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host " [OAG] gateway 路由可达性（4 项）" -ForegroundColor Yellow

    # [2] GET /api/v1/oag/chat/health (gateway 转发 — 健康转发, OagController 自身是 GET 端点)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $code = Get-HttpCode -Url "$GATEWAY/api/v1/oag/chat/health" -Method 'GET'
    $sw.Stop()
    $verdict = if ($code -eq 200) { 'PASS' } else { 'FAIL' }
    Write-Result '2' 'GET /api/v1/oag/chat/health (gateway 转发)' $code $sw.ElapsedMilliseconds $verdict
    $results.Add(@{ Id = 2; Name = 'GET /oag/chat/health (gateway)'; Http = $code; Verdict = $verdict })
    $sw.Stop()

    # [3] POST /api/v1/oag/chat/v2 (gateway — 强类型 DTO 兼容)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $code = Get-HttpCode -Url "$GATEWAY/api/v1/oag/chat/v2" -Method 'POST' -DataFile $tmpV2
    $verdict = if ($code -eq '200' -or $code -eq '201') { 'PASS' } else { 'FAIL' }
    Write-Result '3' 'POST /api/v1/oag/chat/v2 (gateway)' $code $sw.ElapsedMilliseconds $verdict
    $results.Add(@{ Id = 3; Name = 'POST /oag/chat/v2 (gateway)'; Http = $code; Verdict = $verdict })
    $sw.Stop()

    # [4] POST /api/v1/oag/chat (gateway — 兼容 Map 入参)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $code = Get-HttpCode -Url "$GATEWAY/api/v1/oag/chat" -Method 'POST' -DataFile $tmpV1
    $verdict = if ($code -eq '200' -or $code -eq '201') { 'PASS' } else { 'FAIL' }
    Write-Result '4' 'POST /api/v1/oag/chat (gateway 兼容)' $code $sw.ElapsedMilliseconds $verdict
    $results.Add(@{ Id = 4; Name = 'POST /oag/chat (gateway 兼容)'; Http = $code; Verdict = $verdict })
    $sw.Stop()

    # [5] POST /api/v1/oag/chat/stream (SSE — 抓前 $MAX_SSE_FRAMES 帧)
    Write-Host " [SSE] POST /api/v1/oag/chat/stream (max $MAX_SSE_FRAMES 帧, $($Timeout)s)" `
        -ForegroundColor Yellow
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $frames = Read-SseFrames -Url "$GATEWAY/api/v1/oag/chat/stream" -DataFile $tmpV2 `
                 -MaxFrames $MAX_SSE_FRAMES -Sec $Timeout
    $sw.Stop()
    $eventCount = @($frames | Where-Object { $_.kind -eq 'event' }).Count
    $dataCount  = @($frames | Where-Object { $_.kind -eq 'data' }).Count
    $verdict = if ($eventCount -ge 1) { 'PASS' } else { 'FAIL' }
    Write-Result '5' "SSE /api/v1/oag/chat/stream" "SSE/e:$eventCount" `
        $sw.ElapsedMilliseconds $verdict
    $results.Add(@{ Id = 5; Name = 'POST /oag/chat/stream (SSE)'; Http = "SSE(e:$eventCount/d:$dataCount)"; `
                     Verdict = $verdict })
    if ($frames.Count -gt 0) {
        foreach ($f in ($frames | Select-Object -First 4)) {
            $desc = if ($f.kind -eq 'event') { "  node-event <- $($f.text)" } else { "               data: $($f.text)" }
            Write-Host ("     └─ " + $desc) -ForegroundColor DarkGray
        }
    }
} finally {
    if (Test-Path $tmpV2) { Remove-Item $tmpV2 -Force -ErrorAction SilentlyContinue }
    if (Test-Path $tmpV1) { Remove-Item $tmpV1 -Force -ErrorAction SilentlyContinue }
}

# ── 汇总 ─────────────────────────────────────────────────────────
$totalMs = [Environment]::TickCount - $totalStart
Write-Host ""
Write-Host "────────────────── 汇总 ──────────────────" -ForegroundColor Cyan
$pass = @($results | Where-Object { $_.Verdict -eq 'PASS' })
$fail = @($results | Where-Object { $_.Verdict -eq 'FAIL' })
Write-Host ("  PASS {0}/{1}  FAIL {2}/{1}  用时 {3} ms" -f $pass.Count, $results.Count, $fail.Count, $totalMs)
if ($fail.Count -eq 0) {
    Write-Host "  SMOKE: PASS" -ForegroundColor Green
    exit 0
} else {
    Write-Host "  SMOKE: FAIL" -ForegroundColor Red
    foreach ($f in $fail) {
        Write-Host ("   ✗ Id={0} {1} (HTTP {2})" -f $f.Id, $f.Name, $f.Http) -ForegroundColor DarkRed
    }
    exit 1
}
