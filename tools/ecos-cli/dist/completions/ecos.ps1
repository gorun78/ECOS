# ecos CLI — PowerShell 补全（`Import-Module .\ecos.ps1` 后生效）
$EcosCompletions = @{
    'ecos'  = @('doctor', 'cognition', 'capabilities', 'help', 'version');
    'cognition' = @('models');
    'models' = @('list');
    'capabilities' = @('list');
}

$EcosGlobalOptions = @(
    '--output', '--base-url', '--token', '--token-file', '--profile', '--trace-id',
    '--timeout', '--dry-run', '--no-retry', '--all', '--page-size', '--max-pages',
    '--idempotency-key', '--follow', '--show-config', '--help', '--version'
)

Register-ArgumentCompleter -Native -CommandName ecos -ScriptBlock {
    param($wordToComplete, $commandAst, $cursorPosition)
    $tokens = $commandAst.CommandElements | ForEach-Object { $_.ToString() }
    $prev = if ($tokens.Count -ge 2) { $tokens[-1] } else { '' }

    if ($prev -eq '--output') {
        @('json', 'table') | Where-Object { $_ -like "$wordToComplete*" } |
            ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_) }
        return
    }

    $seq = if ($tokens.Count -ge 2) { $tokens[1] } else { 'ecos' }
    $candidates = @()
    if ($EcosCompletions.ContainsKey($seq)) { $candidates += $EcosCompletions[$seq] }
    $candidates += $EcosGlobalOptions

    $candidates | Where-Object { $_ -like "$wordToComplete*" } |
        ForEach-Object { [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_) }
}
