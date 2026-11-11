# ECOS CLI 安装脚本（Windows PowerShell）
#
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/ecos-cli/dist/install.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/ecos-cli/dist/install.ps1 -Jar .\ecos-cli.jar
#
# 行为：
#   1) 校验 JDK ≥ 17
#   2) 复制 fat jar 到 ~/.ecos-cli/lib/
#   3) 生成 ~/.ecos-cli/bin/ecos.cmd 包装器
#   4) 提示把 bin 目录加入 PATH（**不自动改 PATH**，避免动用户环境）
#
# 参数：
#   -Jar      待安装 jar（默认 tools/ecos-cli/target/ecos-cli.jar）
#   -Home     安装根目录（默认 $HOME/.ecos-cli）

[CmdletBinding()]
param(
    [string]$Jar = "",
    [Alias('Home')]
    [string]$InstallHome = ""
)

$ErrorActionPreference = "Stop"

function Resolve-Java {
    $candidates = @()
    if ($env:ECOS_JAVA) { $candidates += $env:ECOS_JAVA }
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\java.exe") }
    $envDir = Join-Path $HOME "env"
    if (Test-Path $envDir) {
        Get-ChildItem $envDir -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue |
            ForEach-Object { $candidates += (Join-Path $_.FullName "bin\java.exe") }
    }
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }

    foreach ($c in $candidates) {
        if ([string]::IsNullOrWhiteSpace($c)) { continue }
        if ($c -ne "java" -and -not (Test-Path $c)) { continue }
        # java -version 走 stderr；PS 5.1 在 $ErrorActionPreference=Stop 下会把 stderr 当终止错误，
        # 必须临时降级并把输出归一成字符串，否则探测恒失败（实测：JDK 明明存在却报"未找到 JDK 17+"）
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $out = ""
        try {
            $out = ((& $c -version 2>&1 | ForEach-Object { "$_" }) -join "`n")
        } catch {
            $out = ""
        } finally {
            $ErrorActionPreference = $prev
        }
        if ($out -match 'version "(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17) { return @{ Path = $c; Major = $major } }
        }
    }
    throw "未找到 JDK 17+。请设置 ECOS_JAVA 或 JAVA_HOME 后重试（CLI 运行时要求 JDK 17+）。"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\")).Path
if ([string]::IsNullOrWhiteSpace($Jar)) {
    $Jar = Join-Path $repoRoot "tools\ecos-cli\target\ecos-cli.jar"
}
if (-not (Test-Path $Jar)) {
    throw "找不到 CLI jar：$Jar`n先构建：mvn -o -f tools/ecos-cli/pom.xml package"
}
if ([string]::IsNullOrWhiteSpace($InstallHome)) {
    $InstallHome = Join-Path $HOME ".ecos-cli"
}

$java = Resolve-Java
Write-Host "[1/4] JDK 检测通过：$($java.Path)（version $($java.Major)）"

$libDir = Join-Path $InstallHome "lib"
$binDir = Join-Path $InstallHome "bin"
New-Item -ItemType Directory -Force -Path $libDir, $binDir | Out-Null

$target = Join-Path $libDir "ecos-cli.jar"
Copy-Item -Path $Jar -Destination $target -Force
Write-Host "[2/4] 已安装 jar：$target"

$ecosCmd = Join-Path $binDir "ecos.cmd"
$javaPath = $java.Path
$wrapper = @"
@echo off
setlocal
set "JAR=$target"
set "JAVA_BIN=$javaPath"
if not exist "%JAR%" (
  echo [ecos] 找不到 %JAR%，请重新运行 install.ps1 1>&2
  exit /b 1
)
"%JAVA_BIN%" -Dfile.encoding=UTF-8 -jar "%JAR%" %*
exit /b %ERRORLEVEL%
"@
Set-Content -Path $ecosCmd -Value $wrapper -Encoding ASCII
Write-Host "[3/4] 已生成包装器：$ecosCmd"

Write-Host "[4/4] 完成。请手动把以下目录加入 PATH（本脚本不会自动改 PATH）："
Write-Host "      $binDir"
Write-Host ""
Write-Host "验证："
Write-Host "      ecos --version"
Write-Host "      ecos doctor --base-url http://localhost:8080"
