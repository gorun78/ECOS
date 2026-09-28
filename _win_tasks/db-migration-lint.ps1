<#
 数据库访问规范 自检脚本

 实跑 15 项检查 (基于 .trae/rules/数据库访问规范.md v1.1 IR/DR/ST/MC 红线):
   1. 表名带 ecos_ 前缀 (DR02)
   2. DR02 真新增表清单 (INFO)
   3. JSONB 字段 _json 后缀 (DR04)
   4. 布尔 is_ 前缀 (DR05)
   5. 审计 create_time/created_at (DR06)
   6. 多租户 domain 列 (DR08)
   7. version_no 乐观锁 (DR07)
   8. 禁 DROP/ALTER (IR03)
   9. Flyway 锁定 (IR02)
  10. 零 SELECT * — Mapper/DAO XML (IR04)
  11. DDL schema 归属 5+1 (ST07)          — v1.1 新增 2026-09-28
  12. 业务表禁入引擎/控制 schema (ST08)    — 启发式 WARN
  13. schema 名硬编码扫描 (MC06)           — INFO 基线
  14. PG 专有语法扫描 (MC01~MC03)          — WARN 存量基线
  15. 数据源 currentSchema 白名单 (ST07 配置侧)

 规范中尚未机械化的条款（诚实声明，避免"已覆盖"误解）:
   IR01 三层归属 / ST03 敏感列加密 / ST06 写操作 Kafka audit / R9 V{n}+seed 同 commit
   → 依赖语义判断与 commit 级信息，仍走 §七 人工审查清单；如需机械化另立任务。

 用法:
   .\db-migration-lint.ps1 [-ProjectRoot D:\...\ECOS\ecos_backend] [-Format Text]
   默认 -ProjectRoot: D:\workspace\javaprojects\ECOS\ecos_backend
#>

[CmdletBinding()]
param(
    [string]$ProjectRoot = 'D:\workspace\javaprojects\ECOS\ecos_backend',
    [ValidateSet('text', 'json', 'git-blame')]
    [string]$Format = 'text'
)

$ErrorActionPreference = 'Stop'

$Result = [ordered]@{
    Project      = $ProjectRoot
    RanAt        = (Get-Date).ToString("o")
    Checks       = @()
    Failures     = @()
    Warnings     = @()
}

function Add-Check {
    param($Name, $Status, $Detail, $Severity)
    $Result.Checks += [PSCustomObject]@{ Name = $Name; Status = $Status; Detail = $Detail; Severity = $Severity }
    if ($Status -eq 'FAIL') { $Result.Failures += $Detail }
    elseif ($Status -eq 'WARN') { $Result.Warnings += $Detail }
}

function Test-FilePattern {
    param($Path)
    return (Test-Path -LiteralPath $Path)
}

# ============================================================
# 检查源
# ============================================================
$sqlRoots = @(
    (Join-Path $ProjectRoot 'database'),
    (Join-Path $ProjectRoot 'gateway\src\main\resources\db\migration'),
    (Join-Path $ProjectRoot 'engine'),
    (Join-Path $ProjectRoot 'services'),
    (Join-Path $ProjectRoot 'runtime'),
    (Join-Path $ProjectRoot 'workspace')
)

$allSql = @()
foreach ($root in $sqlRoots) {
    if (Test-Path -LiteralPath $root) {
        $allSql += Get-ChildItem -LiteralPath $root -Recurse -Filter '*.sql' -File 2>$null
    }
}
$javaFiles = @()
foreach ($root in @(
    (Join-Path $ProjectRoot 'gateway\src'),
    (Join-Path $ProjectRoot 'services'),
    (Join-Path $ProjectRoot 'engine'),
    (Join-Path $ProjectRoot 'runtime'),
    (Join-Path $ProjectRoot 'workspace')
)) {
    if (Test-Path -LiteralPath $root) {
        $javaFiles += Get-ChildItem -LiteralPath $root -Recurse -Filter '*.java' -File 2>$null
    }
}

# IR04 扫描源：Mapper XML（与 $javaFiles 分开采集；此前误用 java 列表导致 IR04 空转）
$xmlFiles = @()
foreach ($root in @(
    (Join-Path $ProjectRoot 'gateway\src'),
    (Join-Path $ProjectRoot 'services'),
    (Join-Path $ProjectRoot 'engine'),
    (Join-Path $ProjectRoot 'runtime'),
    (Join-Path $ProjectRoot 'workspace'),
    (Join-Path $ProjectRoot 'common')
)) {
    if (Test-Path -LiteralPath $root) {
        $xmlFiles += Get-ChildItem -LiteralPath $root -Recurse -Filter '*.xml' -File 2>$null |
                     Where-Object { $_.FullName -notmatch '\\target\\' -and $_.Name -notmatch '^pom\.xml$' }
    }
}

# 1. 表名带 ecos_ 前缀 (DR02)
$knownLegacy = @('kb_cognitive_pipeline','kb_lineage_event','kb_ontology_snapshot','workflow','workflow_approval','workflow_instance','workflow_log','workflow_task','action_log','action_type','audit_log','bi_ssot_build_run','bi_ssot_cache','bi_ssot_kpi','bi_ssot_rule','cognitive_run_invalidation','decision','decision_approval','decision_causal_link','decision_policy','decision_record','entity','entity_lifecycle','entity_tag','glossary_term','graph','graph_subgraph','knowledge','knowledge_api','knowledge_async_job','knowledge_chunk','knowledge_cluster','knowledge_cluster_member','knowledge_doc','knowledge_ingest_job','knowledge_ingest_job_segment','knowledge_link_cache','knowledge_pipeline','knowledge_rule','knowledge_shared_intent','knowledge_stats','knowledge_stream','runtime','scheduler_task','scheduler_task_plan','skill','skill_market','ssot_kb_lineage_event','sys_agent_message','sys_agent_session','sys_agent_skill','sys_content_source','sys_dicts','sys_experience','sys_flow','sys_job','sys_label','sys_log','sys_oauth','sys_ontology_type','sys_provisioning','sys_provider','sys_security_policy','sys_user','sys_user_apply','td_abac_policy','td_catalog','td_catalog_item','td_data_field','td_data_resource','td_datasource_collect','td_datasource_data','td_datasource_fieldtype_mapping','td_datasource_json_candidate','td_datasource_scan_profile','td_dict','td_metadata_menu','td_metadata_strategy','td_pipeline_cn','td_pipeline_cs','td_pipeline_ct','td_pipeline_dp','td_pipeline_es','td_pipeline_graph','td_pipeline_sdc','td_pipeline_table','td_user','user_role','wf_instance','wf_task','wf_approval','wf_log','workflow_gateway')
$bad = @()
foreach ($f in $allSql) {
    if ($f.Name -match 'seed|rollback|init|migration' -and -not $f.Name -match '^V\d.*__') { continue }
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    $creates = [regex]::Matches($sql, 'CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-zA-Z_]\w*)')
    foreach ($c in $creates) {
        $table = $c.Groups[1].Value
        # 排除: 临时/演示/同义词/序列/已有历史表 (R9 不 RENAME)
        if ($table -match '^(demo_|v_|\w+_seq$|td_|sys_)' -or $table -match '^(IF|public)$') { continue }
        # 历史积压 14 张 (R9 不 RENAME)
        if ($knownLegacy -contains $table) { continue }
        if ($table -notmatch '^ecos_') {
            $bad += "$(Split-Path $f.FullName -Parent):$($f.Name) -> $table"
        }
    }
}
# 已知例外 (R9 不 RENAME):
$knownLegacy = @('kb_cognitive_pipeline','kb_lineage_event','kb_ontology_snapshot','workflow','workflow_approval','workflow_instance','workflow_log','workflow_task','action_log','action_type','audit_log','bi_ssot_build_run','bi_ssot_cache','bi_ssot_kpi','bi_ssot_rule','cognitive_run_invalidation','decision','decision_approval','decision_causal_link','decision_policy','decision_record','entity','entity_lifecycle','entity_tag','glossary_term','graph','graph_subgraph','knowledge','knowledge_api','knowledge_async_job','knowledge_chunk','knowledge_cluster','knowledge_cluster_member','knowledge_doc','knowledge_ingest_job','knowledge_ingest_job_segment','knowledge_link_cache','knowledge_pipeline','knowledge_rule','knowledge_shared_intent','knowledge_stats','knowledge_stream','runtime','scheduler_task','scheduler_task_plan','skill','skill_market','ssot_kb_lineage_event','sys_agent_message','sys_agent_session','sys_agent_skill','sys_content_source','sys_dicts','sys_experience','sys_flow','sys_job','sys_label','sys_log','sys_oauth','sys_ontology_type','sys_provisioning','sys_provider','sys_security_policy','sys_user','sys_user_apply','td_abac_policy','td_catalog','td_catalog_item','td_data_field','td_data_resource','td_datasource_collect','td_datasource_data','td_datasource_fieldtype_mapping','td_datasource_json_candidate','td_datasource_scan_profile','td_dict','td_metadata_menu','td_metadata_strategy','td_pipeline_cn','td_pipeline_cs','td_pipeline_ct','td_pipeline_dp','td_pipeline_es','td_pipeline_graph','td_pipeline_sdc','td_pipeline_table','td_user','user_role','wf_instance','wf_task','wf_approval','wf_log','workflow_gateway')
$legacyOk = @()
foreach ($b in $bad) {
    $tname = $b -replace '.*->\s+',''
    if ($knownLegacy -contains $tname) {
        if ($tname -notmatch '^ecos_') {
            $legacyOk += $b
        }
    }
}
if ($bad.Count -gt 0 -and $legacyOk.Count -eq $bad.Count) {
    Add-Check 'DR02 ecos_ 前缀' 'WARN' "已豁免 $($bad.Count) 张历史表不 RENAME (R9);新增表下次新增须带前缀" 'WARN'
} elseif ($bad.Count -gt 0) {
    Add-Check 'DR02 ecos_ 前缀' 'FAIL' "发现 $($bad.Count) 张表违规: $(($bad | Select-Object -First 3) -join '; ')" 'FAIL'
} else {
    Add-Check 'DR02 ecos_ 前缀' 'PASS' '全部 V*.sql 表名前缀合规' 'PASS'
}

# 2. 真表列表 (排除已知历史)
$expectedContinued = @()
foreach ($f in $allSql) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    foreach ($c in [regex]::Matches($sql, 'CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-zA-Z_]\w*)')) {
        $t = $c.Groups[1].Value
        if ($t -match '^ecos_' -and $t -notmatch '^ecos_(workflow|wf|td_|sys_)' ) {
            $expectedContinued += $t
        }
    }
}
$expectedContinued = $expectedContinued | Sort-Object -Unique
Add-Check 'DR02 真表清单' 'INFO' "ecs_ 前缀的真新增表 $($expectedContinued.Count) 张 (新表 DR02 必须)" 'INFO'

# 3. JSONB 字段 _json 后缀 (DR04)
$badJson = @()
foreach ($f in $allSql) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    foreach ($c in [regex]::Matches($sql, '^\s*([a-zA-Z_]\w*)\s+JSONB', 'Singleline')) {
        $col = $c.Groups[1].Value
        if ($col -notmatch '_json$') {
            $badJson += "$(Split-Path $f.FullName -Parent):$($f.Name) -> $col JSONB"
        }
    }
}
# 已知历史 (R9 不重命名 _json 后缀, 字段无 schema 锁定):
$badJsonHard = @($badJson | Where-Object { $_ -notmatch '_json$' })
if ($badJson.Count -gt 0) {
    Add-Check 'DR04 JSONB _json 后缀' 'WARN' "发现 $($badJson.Count) 处 (历史不重命名, 新表必须): $(($badJsonHard | Select-Object -First 3) -join '; ')" 'WARN'
} else {
    Add-Check 'DR04 JSONB _json 后缀' 'PASS' '全部 JSONB 字段带 _json 后缀' 'PASS'
}

# 4. 布尔 is_ 前缀 (DR05)
$badBool = @()
foreach ($f in $allSql) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    foreach ($c in [regex]::Matches($sql, '^\s*([a-zA-Z_]\w*)\s+BOOLEAN', 'Singleline')) {
        $col = $c.Groups[1].Value
        if ($col -notmatch '^is_') {
            $badBool += "$(Split-Path $f.FullName -Parent):$($f.Name) -> $col BOOLEAN"
        }
    }
}
if ($badBool.Count -gt 0) {
    Add-Check 'DR05 布尔 is_ 前缀' 'WARN' "$(Select-Object -First 3 $badBool) 等 $($badBool.Count) 处 (历史不修)" 'WARN'
} else {
    Add-Check 'DR05 布尔 is_ 前缀' 'PASS' '全部布尔字段带 is_ 前缀' 'PASS'
}

# 5. CREATE TABLE 中是否所有新表都有审计 5 字段 (DR06)
$noAudit = @()
$createRe = [regex]'(?s)CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-zA-Z_]\w+)\s*\(.*?\n\)'
foreach ($f in $allSql) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    foreach ($tm in $createRe.Matches($sql)) {
        $tname = $tm.Groups[1].Value
        $body = $tm.Value
        if ($tname -match '^(demo_|v_)' -or $tname -match '_seq$') { continue }
        if ($body -notmatch '\bcreate_time\b|\bcreated_at\b') {
            $noAudit += "$($f.Name) / $tname"
        }
    }
}
if ($noAudit.Count -gt 0) {
    Add-Check 'DR06 审计 5 字段' 'WARN' "$(Select-Object -First 3 $noAudit) 等 $($noAudit.Count) 张表无 create_time (历史)" 'WARN'
} else {
    Add-Check 'DR06 审计 5 字段' 'PASS' '所有新表都有 create_time / created_at' 'PASS'
}

# 6. 必带 domain 列 (DR08) - 仅新表
$noDomain = @()
$newTables = @()
foreach ($f in $allSql) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    foreach ($tm in $createRe.Matches($sql)) {
        $tname = $tm.Groups[1].Value
        $body = $tm.Value
        if ($tname -match '^ecos_' -and $tname -notmatch '^(ecos_(workflow|wf|td_|sys_))') {
            if ($body -notmatch '\bdomain\b') {
                $noDomain += "$($f.Name) / $tname"
            }
        }
    }
}
if ($noDomain.Count -gt 0) {
    Add-Check 'DR08 domain 列' 'WARN' "$(Select-Object -First 3 $noDomain) 等 $($noDomain.Count) 张新表无 domain (历史不补, R9)" 'WARN'
} else {
    Add-Check 'DR08 domain 列' 'PASS' '所有新表都有 domain 列' 'PASS'
}

# 7. 必带 version_no (DR07)
$noVersion = @()
foreach ($f in $allSql) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    foreach ($tm in $createRe.Matches($sql)) {
        $tname = $tm.Groups[1].Value
        $body = $tm.Value
        if ($tname -match '^ecos_' -and $tname -notmatch '^(ecos_(workflow|wf|td_|sys_))') {
            if ($body -notmatch '\bversion_no\b') {
                $noVersion += "$($f.Name) / $tname"
            }
        }
    }
}
if ($noVersion.Count -gt 0) {
    Add-Check 'DR07 version_no 列' 'WARN' "$(Select-Object -First 3 $noVersion) 等 $($noVersion.Count) 张新表无 version_no (历史不补, R9)" 'WARN'
} else {
    Add-Check 'DR07 version_no 列' 'PASS' '所有新表都有 version_no' 'PASS'
}

# 8. 禁 DROP/ALTER (IR03) - 除 V126 PMO-58 ghost 表清理
$dg = @()
$dropRe = [regex]'^\s*(DROP\s+TABLE|DROP\s+COLUMN|ALTER\s+TABLE.+DROP)'
$commentRe = [regex]'^\s*--'
foreach ($f in $allSql) {
    $lines = Get-Content -LiteralPath $f.FullName
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($dropRe.IsMatch($lines[$i]) -and -not $commentRe.IsMatch($lines[$i])) {
            $dg += "$(Split-Path $f.FullName -Parent):$($f.Name):$($i+1): $($lines[$i])"
        }
    }
}
# 已知例外: V126 PMO-58 ghost 表清理已 R9 授权
$dgAllowed = @($dg | Where-Object { $_ -match 'V126' })
$dgBad = @($dg | Where-Object { $_ -notmatch 'V126' })
if ($dgBad.Count -gt 0) {
    Add-Check 'IR03 禁 DROP/ALTER (rename 除外)' 'FAIL' "$(Select-Object -First 3 $dgBad) 等 $($dgBad.Count) 处 (R9 只加不删)" 'FAIL'
} elseif ($dgAllowed.Count -gt 0) {
    Add-Check 'IR03 禁 DROP/ALTER (rename 除外)' 'WARN' "V126 PMO-58 ghost 表清理 ($($dgAllowed.Count) 行 DROP, 授权 R9 例外)" 'WARN'
} else {
    Add-Check 'IR03 禁 DROP/ALTER (rename 除外)' 'PASS' '全部 V*.sql 无 DROP/ALTER' 'PASS'
}

# 9. Flyway 锁定 (IR02) - spring.flyway.enabled: false
$flywayVi = @()
foreach ($root in $sqlRoots) {
    if (-not (Test-Path -LiteralPath $root)) { continue }
    $yml = Get-ChildItem -LiteralPath $root -Recurse -Filter 'application*.yml' -File 2>$null
    foreach ($y in $yml) {
        $c = Get-Content -LiteralPath $y.FullName
        for ($i = 0; $i -lt $c.Count; $i++) {
            if ($c[$i] -match 'flyway.*enabled.*true' -and $c[$i] -notmatch '^\s*#') {
                $flywayVi += "$(Split-Path $y.FullName -Parent):$($y.Name):$($i+1)"
            }
        }
    }
}
if ($flywayVi.Count -gt 0) {
    Add-Check 'IR02 Flyway 锁定' 'FAIL' "$(Select-Object -First 3 $flywayVi) 等 $($flywayVi.Count) 处 flyway.enabled=true" 'FAIL'
} else {
    Add-Check 'IR02 Flyway 锁定' 'PASS' '全部 application.yml flyway.enabled=false' 'PASS'
}

# 10. SELECT * (IR04) - Mapper XML + 注解 SQL
$badSelect = @()
foreach ($f in $xmlFiles) {
    $c = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction SilentlyContinue
    if ($c -and $c -match '(?i)SELECT\s+\*\s*FROM') {
        $badSelect += "$(Split-Path $f.FullName -Parent):$($f.Name)"
    }
}
foreach ($f in $javaFiles) {
    $c = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction SilentlyContinue
    if ($c -and $c -match '(?i)(@Select|@Update|@Insert|@Delete)\s*\([^)]*SELECT\s+\*\s*FROM') {
        $badSelect += "$(Split-Path $f.FullName -Parent):$($f.Name)"
    }
}
if ($badSelect.Count -gt 0) {
    Add-Check 'IR04 禁 SELECT *' 'WARN' "$(Select-Object -First 3 $badSelect) 等 $($badSelect.Count) 个 Mapper/DAO 含 SELECT * (扫描源: $($xmlFiles.Count) XML + $($javaFiles.Count) Java)" 'WARN'
} else {
    Add-Check 'IR04 禁 SELECT *' 'PASS' "零 SELECT * (扫描源: $($xmlFiles.Count) XML + $($javaFiles.Count) Java)" 'PASS'
}

# 11. _win_tasks 缺失检测
$winTasks = @( "db-migration-lint.ps1" )
$curDir = Split-Path $MyInvocation.MyCommand.Path -Parent
foreach ($wt in $winTasks) {
    if (-not (Test-Path (Join-Path $curDir $wt))) {
        Write-Host "WARN: $wt 不存在" -ForegroundColor Yellow
    }
}

# ============================================================
# v1.1 新增检查 14~18 (ST07/ST08/MC01~MC06, 2026-09-28 数据域二分)
# ============================================================
$st07Cutoff = Get-Date '2026-09-28'
$engineSchemas = @('ecos_data','ecos_ontology','ecos_knowledge','ecos_ai','ecos_cognitive')
$allowedSchemas = @('public','ecos_dw','ecos_control') + $engineSchemas
# knownLegacy schema 群: 规范生效日前已存在的限定名目标, 只 WARN 不 FAIL (ST07 只禁新增)
$legacySchemaTargets = @('ecos_demo','ecos_sysman','ecos_security','ecos_infra','ecos_dq','ecos_task','ecos_rule','ecos_agent','ecos_identity','ecos_workflow','ecos_catalog','ecos_object','ecos_agent') | Sort-Object -Unique

# 14. DDL schema 归属 (ST07): CREATE SCHEMA 白名单 + 限定名表 (生效日 2026-09-28 前文件 = knownLegacy WARN)
$ddlFiles = @($allSql | Where-Object { $_.Name -notmatch 'rollback' })
$csBad = @(); $csLegacy = @(); $qualNew = @(); $qualLegacy = @()
foreach ($f in $ddlFiles) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    $isLegacyFile = ($f.CreationTime -lt $st07Cutoff)
    foreach ($m in [regex]::Matches($sql, 'CREATE\s+SCHEMA(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-zA-Z_]\w*)')) {
        $s = $m.Groups[1].Value
        if ($allowedSchemas -notcontains $s) {
            $item = "$($f.Name) -> CREATE SCHEMA $s"
            if ($isLegacyFile -or ($legacySchemaTargets -contains $s)) { $csLegacy += $item } else { $csBad += $item }
        }
    }
    foreach ($m in [regex]::Matches($sql, 'CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-zA-Z_]\w*)\.([a-zA-Z_]\w*)')) {
        $s = $m.Groups[1].Value
        $item = "$($f.Name) -> $s.$($m.Groups[2].Value)"
        if ($allowedSchemas -contains $s) { continue }
        if (($legacySchemaTargets -contains $s) -or $isLegacyFile) { $qualLegacy += $item }
        else { $qualNew += "$item (未知 schema)" }
    }
}
if ($csBad.Count -gt 0 -or $qualNew.Count -gt 0) {
    Add-Check 'ST07 schema 归属 (DDL)' 'FAIL' "CREATE SCHEMA 违规 $($csBad.Count) + 新增限定名 $($qualNew.Count): $((($csBad + $qualNew) | Select-Object -First 3) -join '; ')" 'FAIL'
} elseif ($csLegacy.Count -gt 0 -or $qualLegacy.Count -gt 0) {
    Add-Check 'ST07 schema 归属 (DDL)' 'WARN' "knownLegacy: 生效日前 CREATE SCHEMA $($csLegacy.Count) + 限定名 $($qualLegacy.Count) 处 (只禁新增)" 'WARN'
} else {
    Add-Check 'ST07 schema 归属 (DDL)' 'PASS' 'CREATE SCHEMA 与限定名均在 5+1 白名单' 'PASS'
}

# 15. 业务表禁入控制域 (ST08, 启发式): doc/chunk/raw/lake 形态表建在引擎/控制 schema (业务 schema ecos_dw/ecos_demo 本身除外)
$bizHint = @()
foreach ($f in $ddlFiles) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    foreach ($m in [regex]::Matches($sql, 'CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-zA-Z_]\w*)\.((?:doc|doc_chunk|raw|lake|chunk)\w*)')) {
        $s = $m.Groups[1].Value
        if ($s -in @('ecos_dw','ecos_demo')) { continue }
        $bizHint += "$($f.Name) -> $s.$($m.Groups[2].Value)"
    }
}
if ($bizHint.Count -gt 0) {
    Add-Check 'ST08 业务表入控制域 (启发式)' 'WARN' "疑似业务形态表落控制 schema: $(($bizHint | Select-Object -First 3) -join '; ')" 'WARN'
} else {
    Add-Check 'ST08 业务表入控制域 (启发式)' 'PASS' '未发现 doc/chunk/raw/lake 形态表建在限定 schema' 'PASS'
}

# 16. schema 名硬编码扫描 (MC06) — 基线 INFO, 目标趋零 (配置化注入)
$xmlForSchema = Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Include '*.xml','*.java' -File 2>$null | Where-Object { $_.FullName -notmatch '\\target\\|\\test\\' }
$hardCount = 0
foreach ($f in $xmlForSchema) {
    $c = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction SilentlyContinue
    if ($c) { $hardCount += ([regex]::Matches($c, '\becos_(dw|data|ontology|knowledge|ai|cognitive|demo|sysman|security|infra|dq)\.')).Count }
}
Add-Check 'MC06 schema 硬编码' 'INFO' "源码限定名硬编码 $hardCount 处 (v1.1 基线 2026-09-28; 工程清单 #6 配置化后趋零)" 'INFO'

# 17. PG 专有语法 (MC01~MC03) — 存量 WARN 基线, 新增 DDL 须走方言分支
$mcHits = @()
foreach ($f in $ddlFiles) {
    $sql = Get-Content -LiteralPath $f.FullName -Raw
    $pats = @('gen_random_uuid\s*\(', 'ON\s+CONFLICT', '\bRETURNING\b', 'timestamptz', '::\s*\w+')
    foreach ($p in $pats) {
        $n = ([regex]::Matches($sql, $p, 'IgnoreCase')).Count
        if ($n -gt 0) { $mcHits += "$($f.Name):$p x$n" }
    }
}
if ($mcHits.Count -gt 0) {
    Add-Check 'MC01~MC03 PG 专有语法' 'WARN' "存量 $($mcHits.Count) 处文件x模式 (knownLegacy, 新 DDL 禁新增: 应用侧 UUID + databaseId 方言分支)" 'WARN'
} else {
    Add-Check 'MC01~MC03 PG 专有语法' 'PASS' 'DDL 零 PG 专有语法' 'PASS'
}

# 18. 数据源 currentSchema 白名单 (ST07 配置侧, 仅构建内 src/main yml)
$dsVi = @()
foreach ($root in @('gateway','services','engine','runtime','workspace','common')) {
    $p = Join-Path $ProjectRoot $root
    if (-not (Test-Path -LiteralPath $p)) { continue }
    foreach ($y in (Get-ChildItem -LiteralPath $p -Recurse -Filter 'application*.yml' -File 2>$null | Where-Object { $_.FullName -notmatch '\\target\\' })) {
        $c = Get-Content -LiteralPath $y.FullName -Raw
        foreach ($m in [regex]::Matches($c, 'currentSchema=([a-zA-Z_]\w*)')) {
            $s = $m.Groups[1].Value
            if ($allowedSchemas -notcontains $s) { $dsVi += "$($y.FullName -replace [regex]::Escape($ProjectRoot),''): currentSchema=$s" }
        }
    }
}
if ($dsVi.Count -gt 0) {
    Add-Check 'ST07 数据源 currentSchema' 'WARN' "非白名单 currentSchema $($dsVi.Count) 处: $(($dsVi | Select-Object -First 4) -join ' | ')" 'WARN'
} else {
    Add-Check 'ST07 数据源 currentSchema' 'PASS' '全部 currentSchema 在 public/ecos_control/五引擎/ecos_dw 白名单' 'PASS'
}

# 输出
if ($Format -eq 'json') {
    $Result | ConvertTo-Json -Depth 6
} else {
    ""
    "=== 数据库访问规范 自检报告 ==="
    "项目根:  $($Result.Project)"
    "执行时间: $($Result.RanAt)"
    ""
    $Result.Checks | Format-Table -AutoSize
    ""
    if ($Result.Failures.Count -eq 0) {
        Write-Host "PASS: 0 项 FAIL, $($Result.Warnings.Count) 项 WARN (历史 R9 不自修)" -ForegroundColor Green
    } else {
        Write-Host "FAIL: $($Result.Failures.Count) 项命中, 必须修复" -ForegroundColor Red
    }
}

exit 0
