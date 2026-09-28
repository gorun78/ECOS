# stop-backend.ps1 - stop ECOS processes ONLY on the whitelisted project ports.
# Never kills by process name. Usage:
#   .\stop-backend.ps1                    # stop all 7 v2 ports
#   .\stop-backend.ps1 -Modules gateway,workspace
#   .\stop-backend.ps1 -WithFrontend      # also stop vite :3000
param(
    [string[]]$Modules = @('gateway','sysman','datanet','buszhi','aiming','dccheng','workspace'),
    [switch]$WithFrontend
)
$Ports = @{
    'gateway' = 8080;  'sysman' = 18081; 'datanet' = 18082; 'buszhi' = 18083
    'aiming'  = 18084; 'dccheng' = 18086; 'workspace' = 18090
    # engine boot debug ports (port + 1000 offset used by start-backend.ps1)
    'security-boot' = 19081; 'data-boot' = 19082; 'ontology-boot' = 19083
    'ai-boot' = 19084; 'kb-boot' = 19086; 'cognitive-boot' = 19089
}
$targets = @()
foreach ($m in $Modules) {
    if (-not $Ports.ContainsKey($m)) { Write-Host "[SKIP] unknown module: $m"; continue }
    $targets += $Ports[$m]
}
if ($WithFrontend) { $targets += 3000 }
foreach ($p in ($targets | Select-Object -Unique)) {
    $conns = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { Write-Host "[--] port $p free"; continue }
    foreach ($c in $conns) {
        Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
        Write-Host "[KILLED] port $p (PID $($c.OwningProcess))"
    }
}
