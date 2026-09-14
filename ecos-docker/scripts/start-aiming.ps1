# Aiming service launcher (matching start-gateway.ps1 pattern for OAG forwarding smoke test).
$ErrorActionPreference = 'Stop'
$JAVA = 'C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot\bin\java.exe'
$JAR  = 'D:\workspace\javaprojects\ECOS\ecos_backend\services\aiming\target\aiming-service-1.0.0-SNAPSHOT.jar'
$envFile = Join-Path $env:USERPROFILE '.hermes\profiles\gorunkol\.env'
if (Test-Path $envFile) {
    $line = (Get-Content $envFile | Select-String 'DEEPSEEK_API_KEY' | Select-Object -First 1).Line
    if ($line) { $env:DEEPSEEK_API_KEY = ($line -split '=', 2)[1].Trim() }
    else { Write-Host "[WARN] DEEPSEEK_API_KEY line not found in $envFile" }
} else {
    Write-Host "[WARN] envFile missing: $envFile"
}
$log = 'D:\workspace\javaprojects\ECOS\ecos_backend\_aiming-stdout.log'
Start-Process -FilePath $JAVA -ArgumentList @('-Xms128m','-Xmx512m','-jar',$JAR,'--spring.profiles.active=standard','--server.port=18084') -RedirectStandardOutput $log -WindowStyle Hidden
Start-Sleep -Seconds 8
$p = Test-NetConnection localhost -Port 18084 -InformationLevel Quiet -WarningAction SilentlyContinue
"aiming:18084=$p | env:len=$($env:DEEPSEEK_API_KEY.Length)"
