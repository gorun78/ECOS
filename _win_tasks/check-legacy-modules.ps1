# PMO-C C3：构建前静态检查 — 禁止 legacy 模块入 reactor + 未新增 module
# 验收: 作为 pre-commit 或 CI 前置 gate 运行
# 用法: pwsh -File _win_tasks/check-legacy-modules.ps1

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$rootPom = Join-Path $repo "ecos_backend/pom.xml"

$FAIL = 0

# Check 1: legacy directories must not appear as reactor modules.
# agent-service was REMOVED from this list by PMO-74 H11-T2 (Q4 ruling): it is a real
# reactor module now, referenced by ai-engine-impl and aiming. Leaving it here made this
# gate fail against an approved change (see PMO-74 ledger section 9.6 reverse corrections).
$LEGACY_PATTERNS = @(
    "ontology-service",
    "identity-service",
    "api-gateway",
    "legacy"
)

# Check 1b: ghost module directories must not sit under services/ anymore.
# PMO-74 Q3 batch A moved them to ecos_backend/archive/legacy/services-ghost/.
$ARCHIVED_MODULES = @(
    "ecos_backend/services/api-gateway",
    "ecos_backend/services/identity-service",
    "ecos_backend/services/ontology-service"
)
$GHOST_LANDING_DIR = "ecos_backend/archive/legacy/services-ghost"
foreach ($ghost in $ARCHIVED_MODULES) {
    $ghostPath = Join-Path $repo $ghost
    if (Test-Path $ghostPath) {
        Write-Host "[FAIL] archived module still present under services/: $ghost" -ForegroundColor Red
        $FAIL = 1
    }
}
foreach ($ghost in $ARCHIVED_MODULES) {
    $name = Split-Path -Leaf $ghost
    $landing = Join-Path $repo (Join-Path $GHOST_LANDING_DIR $name)
    if (-not (Test-Path $landing)) {
        Write-Host "[FAIL] archived module missing at landing: $landing" -ForegroundColor Red
        $FAIL = 1
    }
}

$modules = Select-String -Path $rootPom -Pattern "<module>([^<]+)</module>" |
    ForEach-Object { $_.Matches[0].Groups[1].Value.Trim() }

foreach ($legacy in $LEGACY_PATTERNS) {
    $hit = $modules | Where-Object { $_ -match $legacy }
    if ($hit) {
        Write-Host "[FAIL] legacy module '$legacy' found in reactor: $hit" -ForegroundColor Red
        $FAIL = 1
    }
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 检查 2: 未新增 module（module count 不超过基线）
# 基线 = 当前 POM 中去重后的 module 数量
$uniqueModules = $modules | Sort-Object -Unique
$expectedCount = $uniqueModules.Count
# Baseline 12 = previous 11 + services/agent-service (PMO-74 H11-T2 brought it into the
# reactor to kill the ghost dependency). Default reactor per-module count is unchanged.
$baselineCount = 12

if ($expectedCount -gt $baselineCount) {
    Write-Host "[WARN] Module count = $expectedCount (baseline $baselineCount). New modules added!" -ForegroundColor Yellow
    Write-Host "   Modules: $($uniqueModules -join ', ')"
    # 不直接 FAIL（新增 module 需 ADR），仅 WARN
} else {
    Write-Host "[PASS] Module count OK: $expectedCount (baseline $baselineCount)" -ForegroundColor Green
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 检查 3: gateway/ 下不得持有 JdbcTemplate（IR01）
# 注意：这是简化检查——grep POM 级别，非全量源码
$gatewayPom = Join-Path $repo "ecos_backend/gateway/pom.xml"
if (Test-Path $gatewayPom) {
    $gtdeps = Select-String -Path $gatewayPom -Pattern "spring-jdbc|jdbcTemplate" -SimpleMatch
    if ($gtdeps) {
        Write-Host "[FAIL] gateway/pom.xml contains spring-jdbc/JdbcTemplate dependency (IR01)" -ForegroundColor Red
        $FAIL = 1
    }
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

if ($FAIL -eq 0) {
    Write-Host "[PASS] All C3 checks PASS" -ForegroundColor Green
} else {
    Write-Host "[FAIL] C3 checks FAIL - see [FAIL] items above" -ForegroundColor Red
    exit 1
}
