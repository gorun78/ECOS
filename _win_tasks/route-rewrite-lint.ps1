<#
 Route rewrite lint (PMO-74 H9-T6-CHK, and the static half of H9-T5 / PMO-73 G3-T3)

 WHY THIS EXISTS
   VersionPrefixRewriteFilter rewrites /api/v1/<prefix>/** to /api/<prefix>/** for 10 legacy
   prefixes. When a forward entry points at a prefix that NO controller actually serves, every
   request on the canonical v1 path is destroyed at the gateway (HTTP 404 from
   GlobalExceptionHandler) while the legacy bare path still works through the reverse table.
   That defect was found and fixed for /api/v1/knowledge-bases (PMO-74 H9-T6, see ledger
   section 9.69). This lint keeps the whole table under a mechanical gate so the class of bug
   cannot regrow.

 RULES
   R1  FAIL  forward entry whose TARGET prefix is served by nobody AND whose own v1 SOURCE
             prefix IS served by a controller  =>  the entry单方面 destroys a live canonical path.
   R2  WARN  forward entry with no target and no source  =>  dead entry (cleanup candidate).
   R3  PASS  forward entry whose target prefix is served (entry is load-bearing, keep).
   R4  FAIL  reverse entry whose TARGET (v1) prefix is served by nobody => rewrite into a void.
   R5  INFO  report the sysman SecurityConfig permitAll matcher set: it is the only remaining
             anonymity surface (the yml auth.whitelist mechanism was deleted in H9-T5/T5b),
             so this is the scan-object denominator for the anonymous-exposure CI item.

 PATH MODEL
   Spring maps a handler as class-level @RequestMapping concatenated with method-level
   @Get/Post/Put/Delete/PatchMapping. Matching raw literals only under-reports: e.g.
   gateway AlertController = @RequestMapping("/api/v1/alerts") + @GetMapping("/{id}"), which
   has no literal "/api/v1/alerts/{id}". This lint therefore indexes BOTH the raw literals and
   the per-file composed paths, and matches prefixes against the composed set.

 USAGE
   .\route-rewrite-lint.ps1 [-RepoRoot D:\workspace\javaprojects\ECOS]
                            [-FilterPath <VersionPrefixRewriteFilter.java>]
                            [-SecurityConfigPath <SecurityConfig.java>]
                            [-Format text|json] [-Strict]

   Point -FilterPath at a HEAD checkout copy to use this lint as its own positive control:
   the pre-fix tree must report one more R1 than the working tree.

 EXIT  0 clean | 2 R1/R4 present | 3 input missing | 4 -Strict and WARN present
#>

[CmdletBinding()]
param(
    [string]$RepoRoot = 'D:\workspace\javaprojects\ECOS',
    [string]$FilterPath = '',
    [string]$SecurityConfigPath = '',
    [ValidateSet('text', 'json')] [string]$Format = 'text',
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($FilterPath)) {
    $FilterPath = Join-Path $RepoRoot 'ecos_backend\gateway\src\main\java\com\chinacreator\gzcm\gateway\filter\VersionPrefixRewriteFilter.java'
}
if ([string]::IsNullOrWhiteSpace($SecurityConfigPath)) {
    $SecurityConfigPath = Join-Path $RepoRoot 'ecos_backend\services\sysman\impl\sysman-impl\src\main\java\com\chinacreator\gzcm\sysman\security\SecurityConfig.java'
}
if (-not (Test-Path -LiteralPath $FilterPath)) { Write-Output "FATAL filter-not-found: $FilterPath"; exit 3 }

$backendRoot = Join-Path $RepoRoot 'ecos_backend'
if (-not (Test-Path -LiteralPath $backendRoot)) { Write-Output "FATAL backend-root-not-found: $backendRoot"; exit 3 }

function Get-Norm([string]$p) {
    if ([string]::IsNullOrWhiteSpace($p)) { return $p }
    $t = $p.TrimEnd('/')
    if ($t.Length -eq 0) { return '/' }
    return $t
}

# ---------- 1. index every mapped path in the backend (raw literals + composed paths) ----------

$javaFiles = @(Get-ChildItem -LiteralPath $backendRoot -Recurse -Filter *.java -File |
    Where-Object { $_.FullName -notmatch '\\target\\' -and $_.FullName -notmatch '\\archive\\' })

$classRx = [regex]'(?i)RequestMapping\s*\(([^)]*)\)'
$verbRx  = [regex]'(?i)\b(?:Get|Post|Put|Delete|Patch)Mapping\s*\(([^)]*)\)'
$strRx   = [regex]'"([^"]+)"'

$served = New-Object System.Collections.Generic.HashSet[string]
$servedOwner = @{}
$rawLiterals = 0

function Add-Path([string]$p, [string]$owner) {
    if ([string]::IsNullOrWhiteSpace($p)) { return }
    if (-not $p.StartsWith('/')) { return }
    $q = $p -replace '/\{[^}]*\}$', ''
    $q = $q.TrimEnd('/')
    if ($q.Length -eq 0) { $q = '/' }
    [void]$served.Add($q)
    if (-not $servedOwner.ContainsKey($q)) { $servedOwner[$q] = $owner }
}

foreach ($f in $javaFiles) {
    $text = [System.IO.File]::ReadAllText($f.FullName)
    $cls = @(); $verb = @()
    foreach ($m in $classRx.Matches($text)) {
        foreach ($s in $strRx.Matches($m.Groups[1].Value)) {
            $rawLiterals++
            $v = $s.Groups[1].Value
            if ($v.StartsWith('/')) { $cls += $v; Add-Path $v $f.Name }
        }
    }
    foreach ($m in $verbRx.Matches($text)) {
        foreach ($s in $strRx.Matches($m.Groups[1].Value)) {
            $rawLiterals++
            $v = $s.Groups[1].Value
            if ($v.StartsWith('/')) { $verb += $v; Add-Path $v $f.Name }
        }
    }
    foreach ($c in $cls) {
        foreach ($v in $verb) {
            Add-Path (($c.TrimEnd('/')) + $v) $f.Name
        }
        Add-Path $c $f.Name
    }
}

$servedList = @($served | Sort-Object)

# ---------- 2. parse the rewrite tables ----------

$filterText = [System.IO.File]::ReadAllText($FilterPath)
$entryRx = [regex]'Map\.entry\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)'

function Get-MapEntries([string]$blockName) {
    $start = $filterText.IndexOf("$blockName = Map.ofEntries(")
    if ($start -lt 0) { return @() }
    $end = $filterText.IndexOf('    );', $start)
    if ($end -lt 0) { return @() }
    $block = $filterText.Substring($start, $end - $start)
    $out = @()
    foreach ($line in ($block -split "`r?`n")) {
        $t = $line.Trim()
        if ($t.StartsWith('//') -or $t.StartsWith('*')) { continue }
        foreach ($m in $entryRx.Matches($t)) {
            $out += [pscustomobject]@{ Source = $m.Groups[1].Value; Target = $m.Groups[2].Value }
        }
    }
    return $out
}

$forward = @(Get-MapEntries 'V1_REWRITE_MAP')
$reverse = @(@(Get-MapEntries 'REVERSE_EXACT_MAP') + @(Get-MapEntries 'REVERSE_PREFIX_MAP'))

# ---------- 3. anonymity surface (R5) ----------

$permitMatchers = @()
if (Test-Path -LiteralPath $SecurityConfigPath) {
    $secText = [System.IO.File]::ReadAllText($SecurityConfigPath)
    foreach ($m in ([regex]'(?s)requestMatchers\s*\(([^;]*?)\)\s*\.\s*permitAll\s*\(\s*\)').Matches($secText)) {
        foreach ($s in $strRx.Matches($m.Groups[1].Value)) { $permitMatchers += $s.Groups[1].Value }
    }
}
$permitMatchers = @($permitMatchers | Sort-Object -Unique)

# ---------- 4. evaluate ----------

function Find-Prefix([string]$prefix) {
    $norm = Get-Norm $prefix
    $hits = New-Object System.Collections.Generic.List[string]
    foreach ($p in $servedList) {
        if ($p -eq $norm -or $p.StartsWith("$norm/")) { $hits.Add("$p  <== $($servedOwner[$p])") }
    }
    return , @($hits)
}

function Is-Anonymous([string]$prefix) {
    $norm = Get-Norm $prefix
    foreach ($pm in $permitMatchers) {
        $base = ($pm -replace '/\*\*.*$', '')
        if ($norm.StartsWith($base)) { return $true }
    }
    return $false
}

$rows = New-Object System.Collections.Generic.List[object]
$fail = 0
$warn = 0
$dead = 0

foreach ($e in $forward) {
    $tHits = Find-Prefix $e.Target
    $sHits = Find-Prefix $e.Source
    $all = @()
    $all += $tHits
    $all += $sHits
    if ($tHits.Count -eq 0 -and $sHits.Count -gt 0) { $verdict = 'FAIL-R1'; $fail++ }
    elseif ($tHits.Count -eq 0) { $verdict = 'WARN-R2'; $dead++ }
    elseif ($sHits.Count -gt 0) { $verdict = 'WARN-R3'; $warn++ }
    else { $verdict = 'PASS' }
    $rows += [pscustomobject]@{
        Kind = 'forward'; Verdict = $verdict
        Source = $e.Source; Target = $e.Target
        TargetHits = $tHits.Count; SourceHits = $sHits.Count
        Anon = if (Is-Anonymous $e.Source) { 'YES' } else { 'no' }
        Sample = (($all | Select-Object -First 2) -join ' ; ')
    }
}

foreach ($e in $reverse) {
    $tHits = Find-Prefix $e.Target
    if ($tHits.Count -eq 0) { $verdict = 'FAIL-R4'; $fail++ } else { $verdict = 'PASS' }
    $rows += [pscustomobject]@{
        Kind = 'reverse'; Verdict = $verdict
        Source = $e.Source; Target = $e.Target
        TargetHits = $tHits.Count; SourceHits = 0
        Anon = if (Is-Anonymous $e.Target) { 'YES' } else { 'no' }
        Sample = ((@($tHits) | Select-Object -First 2) -join ' ; ')
    }
}

# ---------- 5. report ----------

if ($Format -eq 'json') {
    [ordered]@{
        FilterFile = $FilterPath; JavaFiles = $javaFiles.Count; RawLiterals = $rawLiterals
        ServedPaths = $servedList.Count; Forward = $forward.Count; Reverse = $reverse.Count
        PermitAllMatchers = $permitMatchers; Fail = $fail; Warn = $warn; Dead = $dead; Rows = $rows
    } | ConvertTo-Json -Depth 5
} else {
    Write-Output '== route-rewrite-lint =='
    Write-Output ("filter             : {0}" -f $FilterPath)
    Write-Output ("java files indexed : {0}" -f $javaFiles.Count)
    Write-Output ("mapping literals   : {0}" -f $rawLiterals)
    Write-Output ("served paths       : {0} (raw + composed)" -f $servedList.Count)
    Write-Output ("forward entries    : {0}" -f $forward.Count)
    Write-Output ("reverse entries    : {0}" -f $reverse.Count)
    Write-Output ("permitAll matchers : {0}  [ {1} ]" -f $permitMatchers.Count, ($permitMatchers -join ' '))
    Write-Output ''
    $rows | Format-Table -AutoSize Verdict, Kind, Source, Target, TargetHits, SourceHits, Anon
    foreach ($r in $rows) {
        if ($r.Verdict -ne 'PASS') {
            Write-Output ("[{0}] {1} -> {2}  targetHits={3} sourceHits={4} anon={5}  sample: {6}" -f `
                $r.Verdict, $r.Source, $r.Target, $r.TargetHits, $r.SourceHits, $r.Anon, $r.Sample)
        }
    }
    Write-Output ''
    Write-Output ("RESULT fail={0} warnR3={1} deadR2={2} denominator={3}" -f $fail, $warn, $dead, $rows.Count)
}

if ($fail -gt 0) { exit 2 }
if ($Strict -and ($warn -gt 0 -or $dead -gt 0)) { exit 4 }
exit 0
