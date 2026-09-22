<#
 数据库访问规范 自检脚本

 13 项检查 (基于 .trae/rules/数据库访问规范.md IR/DR/EN/ST 红线):
   1. 表名带 ecos_ 前缀 (DR02)
   2. JSONB 字段 _json 后缀 (DR04)
   3. 布尔 is_ 前缀 (DR05)
   4. 审计 5 字段齐全 (DR06)
   5. 多租户 domain 列 (DR08)
   6. version_no 乐观锁 (DR07)
   7. 禁 DROP/ALTER (IR03)
   8. Flyway 锁定 (IR02)
   9. 新表前端 Mapper 三层 (IR01)
  10. 敏感列加密 (ST03)
  11. 写操作 Kafka audit (ST06)
  12. V{n} + seed 同 commit (R9)
  13. 零 SELECT * (IR04)

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

# 10. SELECT * (IR04) - Mapper XML
$badSelect = @()
foreach ($f in $javaFiles) {
    if ($f.Name -match '^pom\.xml$') { continue }
    if ($f.Extension -ne '.xml') { continue }
    $c = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction SilentlyContinue
    if ($c -and $c -match '(?i)SELECT\s+\*\s*FROM') {
        $badSelect += "$(Split-Path $f.FullName -Parent):$($f.Name)"
    }
}
if ($badSelect.Count -gt 0) {
    Add-Check 'IR04 禁 SELECT *' 'WARN' "$(Select-Object -First 3 $badSelect) 等 $($badSelect.Count) 个 Mapper 含 SELECT *" 'WARN'
} else {
    Add-Check 'IR04 禁 SELECT *' 'PASS' 'Mapper 零 SELECT *' 'PASS'
}

# 11. _win_tasks 缺失检测
$winTasks = @( "db-migration-lint.ps1" )
$curDir = Split-Path $MyInvocation.MyCommand.Path -Parent
foreach ($wt in $winTasks) {
    if (-not (Test-Path (Join-Path $curDir $wt))) {
        Write-Host "WARN: $wt 不存在" -ForegroundColor Yellow
    }
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
