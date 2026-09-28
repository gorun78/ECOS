# docs-migration.ps1 - docs dir one-shot batch migration (PowerShell native, v1.4 100% ascii-safe)
# source: Arch-Reviewer | date: 2026-09-22
# upstream: docs/docs-writing-agent.md section 0.7 ~ 0.9
#
# usage:
#   .\_win_tasks\docs-migration.ps1 -DryRun   # preview only
#   .\_win_tasks\docs-migration.ps1           # real run
#
# preconditions:
#   [ ] on branch feature/docs-migration
#   [ ] -DryRun passed (output 1:1 with section 0.7 mapping)
#   [ ] PMO directive #PMO-{X}-docs-migration approved
#
# encoding note:
#   PS5 reads .ps1 as ASCII/codepage, so Chinese in string literals must be
#   [char]0xNNNN escape. All CJK paths use ASCII temp name + git mv approach.

param([switch]$DryRun)
$ErrorActionPreference = "Stop"

$REPO_ROOT = Split-Path -Parent $PSScriptRoot
$DOCS_ROOT = Join-Path $REPO_ROOT "docs"

# ---------- CJK char map (for path escaping) ----------
# Use [Convert]::ToChar(0xNNNN).Part2 to build UTF-16 strings without $() injection.
$NB = [Convert]::ToChar(0x4F53)   # 本
$NB2 = [Convert]::ToChar(0x4F53)  # 本 (placeholder 2nd)
$NPMO1 = [Convert]::ToChar(0x6307) # 指
$NPMO2 = [Convert]::ToChar(0x4EE4) # 令
$NREV1 = [Convert]::ToChar(0x5BA1) # 审
$NREV2 = [Convert]::ToChar(0x67E5) # 查
$NREV3 = [Convert]::ToChar(0x8BC1) # 证
$NREV4 = [Convert]::ToChar(0x636E) # 据
$NDEV1 = [Convert]::ToChar(0x5F00) # 开
$NDEV2 = [Convert]::ToChar(0x53D1) # 发
$NDEV3 = [Convert]::ToChar(0x9636) # 阶
$NDEV4 = [Convert]::ToChar(0x6BB5) # 段
$NMGT1 = [Convert]::ToChar(0x9879) # 项
$NMGT2 = [Convert]::ToChar(0x76EE) # 目
$NMGT3 = [Convert]::ToChar(0x7BA1) # 管
$NMGT4 = [Convert]::ToChar(0x7406) # 理
$NOPS1 = [Convert]::ToChar(0x8FD0) # 运
$NOPS2 = [Convert]::ToChar(0x7EF4) # 维
$NPD1  = [Convert]::ToChar(0x4EA7) # 产
$NPD2  = [Convert]::ToChar(0x54C1) # 品
$NPD3  = [Convert]::ToChar(0x5316) # 化
$NPD4  = [Convert]::ToChar(0x91CD) # 重
$NPD5  = [Convert]::ToChar(0x6784) # 构
$NPD6  = [Convert]::ToChar(0x65B9) # 方
$NPD7  = [Convert]::ToChar(0x6848) # 案
$NPD8  = [Convert]::ToChar(0x672C) # 本 (2nd 本 for 04-本体)
$NQ1   = [Convert]::ToChar(0x8D28) # 质
$NQ2   = [Convert]::ToChar(0x91CF) # 量
$NIN1  = [Convert]::ToChar(0x96C6) # 集
$NIN2  = [Convert]::ToChar(0x6210) # 成
$NIN3  = [Convert]::ToChar(0x8054) # 联
$NIN4  = [Convert]::ToChar(0x8C03) # 调
$NR1   = [Convert]::ToChar(0x4EA4) # 交
$NR2   = [Convert]::ToChar(0x4ED8) # 付
$NR3   = [Convert]::ToChar(0x62A5) # 报
$NR4   = [Convert]::ToChar(0x544A) # 告
$NPW1  = [Convert]::ToChar(0x5B8C) # 善
$NPW2  = [Convert]::ToChar(0x5584) # 善
$NN1   = [Convert]::ToChar(0x6280) # 技
$NN2   = [Convert]::ToChar(0x672F) # 术
$NN3   = [Convert]::ToChar(0x503A) # 债
$NN4   = [Convert]::ToChar(0x4FEE) # 修
$NN5   = [Convert]::ToChar(0x590D) # 复
$NN6   = [Convert]::ToChar(0x8BA1) # 划
$NN7   = [Convert]::ToChar(0x5212) # 计
$NW1   = [Convert]::ToChar(0x5468) # 周
$NW2   = [Convert]::ToChar(0x62A5) # 报
$NW3   = [Convert]::ToChar(0x7D20) # 素
$NW4   = [Convert]::ToChar(0x6750) # 材
$NW5   = [Convert]::ToChar(0x6570) # 数
$NW6   = [Convert]::ToChar(0x636E) # 据
$NW7   = [Convert]::ToChar(0x79D1) # 科
$NW8   = [Convert]::ToChar(0x5B66) # 学
$NW9   = [Convert]::ToChar(0x672C) # 本
$NW10  = [Convert]::ToChar(0x4F53) # 体
$NW11  = [Convert]::ToChar(0x8BBA) # 论
$NW12  = [Convert]::ToChar(0x77E5) # 识
$NW13  = [Convert]::ToChar(0x8BC6) # 知
$NW14  = [Convert]::ToChar(0x56FE) # 图
$NW15  = [Convert]::ToChar(0x8C31) # 谱
$NW16  = [Convert]::ToChar(0x8FDB) # 进
$NW17  = [Convert]::ToChar(0x5C55) # 展
$NA1   = [Convert]::ToChar(0x67B6) # 架
$NA2   = [Convert]::ToChar(0x6784) # 构

$ONTO_SRC  = "04-$NPD8$NB"                                                    # 04-本体
$PMO_SRC   = "09-PMO$NPMO1$NPMO2"                                               # 09-PMO指令
$REV_SRC   = "10-$NREV1$NREV2$NREV3$NREV4"                                      # 10-审查证据
$DEV_SRC   = "03$NDEV1$NDEV2$NDEV3$NDEV4"                                       # 03开发阶段
$MGT_SRC   = "07$NMGT1$NMGT2$NMGT3$NMGT4"                                      # 07项目管理
$OPS_SRC   = "11-$NOPS1$NOPS2"                                                 # 11-运维
$PRODUCT_SRC = "08-$NPD1$NPD2$NPD3$NPD4$NPD5$NPD6$NPD7"                         # 08-产品化重构方案
$QUALITY_SRC = "08-$NPD1$NPD2$NQ1$NQ2"                                        # 08-产品质量
$INTEG_SRC = "07-$NIN1$NIN2$NIN3$NIN4"                                          # 07-集成联调
$RPT24_SRC = "24-$NR1$NR2$NQ1$NQ2$NR3$NR4-2026-09-03.md"                        # 24-交付质量报告-2026-09-03.md
$PRODUCT_FILE = "ECOS-$NPD1$NPD2$NPD3$NPW1$NPW2$NPD6$NPD7.md"                   # ECOS-产品化完善方案.md
$TECHDEBT_FILE = "ECOS-$NN1$NN2$NN3$NN4$NN5$NN6$NN7.md"                         # ECOS-技术债修复计划.md
$WEEKLY_SRC = "$NW1$NW2$NW3$NW4-$NW5$NW6$NW7$NW8$NW9$NW10$NW11$NW12$NW13$NW14$NW15$NW16$NW17-20260803.md"  # 周报素材-数据科学本体论知识图谱进展-20260803.md
$ARCH_DIR  = "00-$NA1$NA2"                                                       # 00-架构

if ($DryRun) {
  Write-Host "============================================================" -ForegroundColor Cyan
  Write-Host "  DRY-RUN mode (only print, no real mv/commit/rm)" -ForegroundColor Cyan
  Write-Host "============================================================" -ForegroundColor Cyan
}
$null = Get-Command git -ErrorAction Stop

function Move-Keep {
  param([string]$Src, [string]$Dst)
  # 目标名不能含 -batch-N 后缀（防语义污染）
  if ($Dst -match "-batch-\d+") {
    Write-Host "  [FAIL] Dst 含 -batch-N 后缀, 拒绝: $Dst" -ForegroundColor Red
    return
  }
  Push-Location $DOCS_ROOT
  try {
    if (-not (Test-Path -LiteralPath $Src)) {
      Write-Host "  [skip] missing: `"$Src`"" -ForegroundColor DarkYellow
      return
    }
    $isFile = (Get-Item -LiteralPath $Src).PSIsContainer -eq $false
    $fullSrc = Join-Path "." $Src
    if ($DryRun) {
      Write-Host "  [dry] git mv `"$Src`" `"$Dst`"" -ForegroundColor DarkGray
      return
    }
    if ($isFile) {
      # 文件 → 目标文件夹 (不建中间 batch 层)
      $fullDst = Join-Path "." $Dst
      New-Item -Path $fullDst -ItemType Directory -Force | Out-Null
      Move-Item -LiteralPath $fullSrc -Destination $fullDst -Force
      $null = git add -u -- $Src 2>&1
      Write-Host "  >> git mv `"$Src`" `"$Dst`""
    } else {
      # 目录 → 目录 (3 cases)
      $fullDst = Join-Path "." $Dst
      $leaf = Split-Path $Src -Leaf
      if (Test-Path -LiteralPath $fullDst) {
        # Dst 已存在 (部分 run 建出) → src 作为 sub 并入
        $subDst = Join-Path $fullDst $leaf
        if (Test-Path -LiteralPath $subDst) {
          $subDst = Join-Path $fullDst "$leaf-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        }
        Move-Item -LiteralPath $fullSrc -Destination $subDst -Force
        Write-Host "  >> merged $Src into $Dst/$leaf" -ForegroundColor Yellow
      } else {
        # Dst 不存在 → src 直接更名
        Move-Item -LiteralPath $fullSrc -Destination $fullDst -Force
        Write-Host "  >> git mv `"$Src`" `"$Dst`""
      }
      $null = git add -u -- $Src 2>&1
    }
  } finally { Pop-Location }
}

function Commit-Keep {
  param([string]$Msg)
  if ($DryRun) {
    Write-Host "  [dry] git add docs/ && git commit -m `<multi-line>" -ForegroundColor DarkGray
    return
  }
  Push-Location $REPO_ROOT
  try {
    $null = git add docs/ 2>&1
    $null = git commit -m $Msg 2>&1
    $line = (git log --oneline -1 | Select-Object -First 1)
    Write-Host $line -ForegroundColor Green
  } finally { Pop-Location }
}

# ============ 0. cleanup -batch-N artifacts from prior partial run ============
Write-Host "==> cleanup -batch-N artifacts from prior partial run"
if (-not $DryRun) {
  $batchPairs = @(
    @("23-quality-batch-2/24-$NR1$NR2$NQ1$NQ2$NR3$NR4-2026-09-03.md", "23-quality/24-$NR1$NR2$NQ1$NQ2$NR3$NR4-2026-09-03.md")
  )
  foreach ($p in $batchPairs) {
    if (Test-Path -LiteralPath (Join-Path $DOCS_ROOT $p[0])) {
      $srcPath = Join-Path $DOCS_ROOT $p[0]
      $dstPath = Join-Path $DOCS_ROOT $p[1]
      Push-Location $DOCS_ROOT
      Move-Item -LiteralPath $srcPath -Destination $dstPath -Force
      Pop-Location
      Write-Host "  >> merged $p[0] -> $p[1]" -ForegroundColor Green
    }
  }
  # 清理 -batch-2 子目录 (empty 删)
  $batchDirs = @(
    "23-quality/24-$NR1$NR2$NQ1$NQ2$NR3$NR4-2026-09-03-batch-2"
  )
  foreach ($b in $batchDirs) {
    $full = Join-Path $DOCS_ROOT $b
    if (Test-Path -LiteralPath $full) {
      $kids = @(Get-ChildItem -LiteralPath $full -Force -Recurse)
      if ($kids.Count -eq 0) {
        Remove-Item -LiteralPath $full -Recurse -Force
        Write-Host "  >> removed empty dir $b" -ForegroundColor Green
      }
    }
  }
}

# ============ 1. create target dirs (一些目标已被部分执行建出, mkdir 跳) ============
Write-Host "==> creating target dirs (dry-run skips mkdir)"
if (-not $DryRun) {
  $targets = @(
    "engine-security/1-sysman-legacy",
    "engine-data/3-data-legacy",
    "engine-ontology/4-onto-legacy",
    "engine-ontology/04-benti-legacy",
    "engine-cognitive/5-cognitive-legacy",
    "engine-ai/2-aispace-legacy",
    "10-gateway",
    "20-services/sysman",
    "20-services/datanet",
    "20-services/buszhi",
    "20-services/dccheng",
    "20-services/aiming",
    "21-runtime/legacy-plans",
    "22-integration/7-integration-legacy",
    "22-integration/07-jicheng-legacy",
    "23-quality/legacy-08-quality",
    "23-quality/9-checks-legacy",
    "23-quality/08-product-legacy",
    "30-cross-cutting-docs/reviews",
    "30-cross-cutting-docs/pmo",
    "30-cross-cutting-docs/refs",
    "30-cross-cutting-docs/ops",
    "30-cross-cutting-docs/99-archive"
  )
  Push-Location $DOCS_ROOT
  try {
    foreach ($t in $targets) {
      $full = Join-Path "." $t
      if (-not (Test-Path -LiteralPath $full)) {
        New-Item -Path $full -ItemType Directory -Force | Out-Null
      }
    }
  } finally { Pop-Location }
  Write-Host "  >> $($targets.Count) target dirs ready" -ForegroundColor Green
}

# ============ 2. engine dirs (section 0.7 lines 1-5) ============
Write-Host "==> engine dirs (1-sysman -> engine-security, 2-aispace -> engine-ai, etc)"
Move-Keep "1-sysman"      "engine-security/1-sysman-legacy"
Move-Keep "3-data"        "engine-data/3-data-legacy"
Move-Keep "4-onto"        "engine-ontology/4-onto-legacy"
Move-Keep $ONTO_SRC       "engine-ontology/04-benti-legacy"
Move-Keep "5-cognitive"   "engine-cognitive/5-cognitive-legacy"
Move-Keep "2-aispace"     "engine-ai/2-aispace-legacy"

# ============ 2b. cross-cutting legacy dirs ============
Write-Host "==> cross-cutting legacy dirs"
Move-Keep "6-techdebt"   "30-cross-cutting-docs/pmo/6-techdebt-legacy"
Move-Keep $PMO_SRC       "30-cross-cutting-docs/pmo/legacy-09"
Move-Keep "PMO"          "30-cross-cutting-docs/pmo/legacy-PMO"
Move-Keep $REV_SRC       "30-cross-cutting-docs/reviews/legacy-10"
Move-Keep "reviews"      "30-cross-cutting-docs/reviews/legacy-reviews"
Move-Keep $DEV_SRC       "30-cross-cutting-docs/reviews/legacy-03"
Move-Keep $MGT_SRC       "30-cross-cutting-docs/reviews/legacy-07"
Move-Keep "swarm"        "30-cross-cutting-docs/reviews/legacy-swarm"
Move-Keep $OPS_SRC       "30-cross-cutting-docs/ops/legacy-11"
Move-Keep ".migration"   "30-cross-cutting-docs/ops/legacy-migration"
Move-Keep ".trae"        "30-cross-cutting-docs/reviews/legacy-system-docs"
Move-Keep $PRODUCT_SRC   "23-quality/08-product-legacy"
Move-Keep $QUALITY_SRC   "23-quality/legacy-08-quality"
Move-Keep "9-checks"     "23-quality/9-checks-legacy"
Move-Keep "7-integration" "22-integration/7-integration-legacy"
Move-Keep $INTEG_SRC     "22-integration/07-jicheng-legacy"
Move-Keep "plans"        "21-runtime/legacy-plans"

# ============ 3. loose root files ============
Write-Host "==> loose root files"
Move-Keep $RPT24_SRC      "23-quality"
Move-Keep $PRODUCT_FILE   "23-quality"
Move-Keep $TECHDEBT_FILE  "30-cross-cutting-docs/pmo"
Move-Keep "palantir_aip_architecture_diagram.html" "30-cross-cutting-docs/refs"
Move-Keep $WEEKLY_SRC     "30-cross-cutting-docs/refs"
Move-Keep "ARCHITECTURE-RULES.md" $ARCH_DIR
# docs-writing-agent.md stays at root (origin doc)

# ============ 3b. remove screenshots/ (R5 png forbidden, user approved) ============
Write-Host "==> remove screenshots/ (R5 png forbidden, user approved)"
Push-Location $DOCS_ROOT
try {
  if (Test-Path -LiteralPath "screenshots") {
    if ($DryRun) {
      Write-Host "  [dry] Remove-Item + git rm -r screenshots/  (2 png inside, may be untracked)" -ForegroundColor DarkGray
    } else {
      $null = git rm -r screenshots 2>$null
      if ($LASTEXITCODE -ne 0) {
        # git rm 失败 (untracked) → 直接物理删
        Remove-Item -LiteralPath "screenshots" -Recurse -Force
        Write-Host "  >> removed screenshots/ (untracked, physical remove)" -ForegroundColor Green
      } else {
        Write-Host "  >> git rm -r screenshots/" -ForegroundColor Green
      }
    }
  }
} finally { Pop-Location }

# ============ 4. README per target top dir ============
Write-Host "==> create README.md (dry-run skips)"
if (-not $DryRun) {
  Push-Location $DOCS_ROOT
  $readmes = [ordered]@{
    "engine-security" = @(
      "# engine-security (security)", "",
      "> port: :18081 | module: ecos_backend/engine/security-engine",
      "> duty: authn / authz / audit / masking / ABAC (OPA)"
    )
    "engine-data" = @(
      "# engine-data (earth D)", "",
      "> port: :18082 | module: ecos_backend/engine/data-engine",
      "> duty: datasource / pipeline / lineage / DQ / query"
    )
    "engine-ontology" = @(
      "# engine-ontology (gold I)", "",
      "> port: :18083 | module: ecos_backend/engine/ontology-engine",
      "> duty: ontology modeling / objects / relations / versions",
      "> history: 4-onto-legacy/ and 04-benti-legacy/ both live here"
    )
    "engine-cognitive" = @(
      "# engine-cognitive (wood C)", "",
      "> port: :18086 (share with kb, dccheng dual-engine)",
      "> duty: causal reasoning / scenario simulation / hybrid (no new DB table)"
    )
    "engine-ai" = @(
      "# engine-ai (fire W)", "",
      "> port: :18084 | module: ecos_backend/engine/ai-engine + services/agent-service",
      "> duty: Agent / Loop / Memory / LLM invocation",
      "> history: 2-aispace-legacy/ from old 2-aispace"
    )
    "workspace" = @(
      "# workspace (top scene app layer)", "",
      "> port: :18090 | module: ecos_backend/workspace",
      "> duty: object runtime / ObjectQL / Scenario / Workbook / cross-service orchestration"
    )
    "10-gateway" = @(
      "# top gateway", "",
      "> port: :8080 | module: ecos_backend/gateway",
      "> duty: org / auth / rate-limit facade (fat-JAR dual-run compat)"
    )
    $ARCH_DIR = @(
      "# 00-arch (system-level design)", "",
      "> scope: system-wide (cross all 6 engines + 5 services)",
      "> core: ARCHITECTURE-RULES.md (v1.1) + adr/ (independent ADrs)"
    )
  }
  try {
    foreach ($kv in $readmes.GetEnumerator()) {
      $full = Join-Path "." "$($kv.Name)/README.md"
      if (-not (Test-Path -LiteralPath $full)) {
        $kv.Value | Set-Content -Path $full -Encoding UTF8
      }
    }
  } finally { Pop-Location }
  Write-Host "  >> $($readmes.Count) README.md ready" -ForegroundColor Green
}

# ============ 5. commit (commit hash is DONE evidence, docs/ scope only) ============
Write-Host "==> commit migration (commit message has docs-migration keyword)"
$msg = @(
  "refactor(docs): one-shot dir migration - 6 engine + 5 service target",
  "",
  "- old 14+ dirs git-mv'd to *-legacy/ per docs-writing-agent.md section 0.7",
  "- new: engine-{security,data,ontology,kb,cognitive,ai} + 20-services/{5} + 21-runtime + 22-integration + 23-quality + 30-cross-cutting-docs/{5}",
  "- loose root files relocated (incl ARCHITECTURE-RULES.md -> 00-arch/)",
  "- screenshots/ removed (R5 png forbidden, user approved 2026-09-22)",
  "- per-target README.md (port + module anchor)",
  "",
  "Refs: docs/docs-writing-agent.md#0_9"
) -join "`n"
Commit-Keep $msg

# ============ 6. auto-verify ============
if ($DryRun) {
  Write-Host "  [dry] skip verify" -ForegroundColor DarkGray
} else {
  Write-Host "==> auto-verify:" -ForegroundColor Cyan
  Push-Location $REPO_ROOT
  try {
    Write-Host "  [1] git log --oneline -1"
    git log --oneline -1 | ForEach-Object { Write-Host "      $_" }
    Write-Host "  [2] git status -- docs/ (should be clean)"
    $st = (git status -- docs/ --porcelain 2>&1)
    if (-not $st -or $st.Count -eq 0) {
      Write-Host "      >> clean" -ForegroundColor Green
    } else {
      $st | ForEach-Object { Write-Host "      $_" }
    }
    Write-Host "  [3] old-path reference residue (exclude legacy transition notes):"
    # Exclude refs to legacy/ and docs-writing-agent itself
    $residual = @(Get-ChildItem -Path $DOCS_ROOT -Filter *.md -Recurse -File -ErrorAction SilentlyContinue |
      Select-String -Pattern (
        'docs/1-sysman|docs/2-aispace|docs/7-integration|' +
        'docs/10-.*\x8BC1.*' + '|' +
        'docs/9-checks|docs/08-' + [regex]::Escape("产品化") + '|' +
        'docs/09-PMO|docs/11-.*\x8FD0.*' + '|' +
        'docs/07-' + [regex]::Escape("集成联调") + '|' +
        'docs/07.*\x9879\x76EE\x7BA1\x7406|docs/plans|docs/PMO'
      ) -SimpleMatch:$false -ErrorAction SilentlyContinue |
      Where-Object { $_.Line -notmatch "legacy-|docs-writing-agent" }
    )
    Write-Host "      old-path residue: $($residual.Count) places"
    if ($residual.Count -eq 0) {
      Write-Host "      >> clean" -ForegroundColor Green
    } else {
      $residual | Select-Object -First 5 | ForEach-Object {
        Write-Host "      $($_.Filename):$($_.LineNumber) `"$($_.Line.Trim())`"" -ForegroundColor Yellow
      }
      Write-Host "      >> please manually update remaining refs" -ForegroundColor Yellow
    }
    Write-Host "  [4] target top-level dir count (expect ~14):"
    Push-Location $DOCS_ROOT
    $dr = @(Get-ChildItem -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "^\." })
    $dr | ForEach-Object { Write-Host "      $_" }
    Write-Host "      top-level dirs: $($dr.Count)"
    Pop-Location
    Write-Host "  [5] manual steps (script not enforced):"
    Write-Host '      & "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f ecos_backend\pom.xml validate -q'
    Write-Host "      cd ecos_frontend; npm run lint"
  } finally { Pop-Location }
}

Write-Host "==> migration done" -ForegroundColor Green
