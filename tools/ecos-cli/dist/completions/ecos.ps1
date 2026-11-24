# ecos CLI — PowerShell 补全（由 `ecos --completion ps1` 生成，请勿手改）
Register-ArgumentCompleter -Native -CommandName ecos -ScriptBlock {
    param($wordToComplete, $commandAst, $cursorPosition)
    $candidates = @(
        'doctor',
        'cognition',
        'capabilities',
        'ontology',
        'model',
        'runtime',
        'help',
        'version',
        'models',
        'list',
        'model',
        'validate',
        'run',
        'list',
        'validate',
        'list',
        'route',
        'doctor',
        '--output',
        '--base-url',
        '--token',
        '--token-file',
        '--profile',
        '--trace-id',
        '--timeout',
        '--dry-run',
        '--no-retry',
        '--all',
        '--page-size',
        '--max-pages',
        '--idempotency-key',
        '--approval-token',
        '--body-json',
        '--follow',
        '--last-event-id',
        '--show-config',
        '--preflight',
        '--help',
        '--version',
        '--completion',
    )
    $candidates | Where-Object { $_ -like "$wordToComplete*" } | ForEach-Object {
        [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_)
    }
}
