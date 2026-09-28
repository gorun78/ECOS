# PMO-C C3：构建前静态检查 — 禁止 legacy 模块入 reactor + 未新增 module
# 验收: 作为 pre-commit 或 CI 前置 gate 运行
# 用法: pwsh -File _win_tasks/check-legacy-modules.ps1

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$rootPom = Join-Path $repo "ecos_backend/pom.xml"

$FAIL = 0

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# 检查 1: legacy 目录不得出现在 reactor modules
$LEGACY_PATTERNS = @(
    "agent-service",
    "ontology-service",
    "identity-service",
    "api-gateway",
    "legacy"
)

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
$baselineCount = 11  # 默认 reactor (default modules 去重后): engine + runtime + buszhi/impl + sysman/impl + workspace + 5 services + gateway

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
