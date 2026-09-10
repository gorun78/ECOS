# ═══════════════════════════════════════════════════════════
# ECOS WSL → Windows 迁移脚本
# 用法: 以管理员身份打开 PowerShell，执行:
#   cd D:\workspace
#   powershell -ExecutionPolicy Bypass -File <此脚本路径>
# (或直接双击运行，前提是 D:\workspace 目录已存在)
# ═══════════════════════════════════════════════════════════

$ErrorActionPreference = 'Stop'
$SRC = '\\wsl$\Ubuntu\home\guorongxiao\ECOS'
$DST = 'D:\workspace\javaprojects\ECOS'

Write-Output "══════════════════════════════════════════"
Write-Output "  ECOS WSL → Windows Migration"
Write-Output "  Source: $SRC"
Write-Output "  Target: $DST"
Write-Output "══════════════════════════════════════════"

# ── 1. Pre-check ──
if (Test-Path $DST) {
    Write-Output "[ERROR] $DST 已存在。请删除或修改 $DST 后重试。"
    exit 1
}
if (!(Test-Path 'D:\workspace')) {
    New-Item -ItemType Directory -Path 'D:\workspace' -Force | Out-Null
    Write-Output "[OK] 已创建 D:\workspace"
}
if (!(Test-Path 'D:\workspace\javaprojects')) {
    New-Item -ItemType Directory -Path 'D:\workspace\javaprojects' -Force | Out-Null
    Write-Output "[OK] 已创建 D:\workspace\javaprojects"
}

# ── 2. Robocopy 高速复制 (排除 node_modules/.git/target/.m2) ──
Write-Output "`n[1/4] 文件复制 (robocopy, 排除 node_modules/.git/target/.m2)..."
$sw = [System.Diagnostics.Stopwatch]::StartNew()
robocopy $SRC $DST /E `
    /XD node_modules .git target .m2
    /NFL /NDL /NJH /NJS /NC /NS /NP `
    /R:2 /W:1 2>&1 | Out-Null
$rc = $LASTEXITCODE
$sw.Stop()
if ($rc -ge 8) {
    Write-Output "[ERROR] robocopy 失败, exit=$rc"
    exit 1
}
Write-Output "[OK] 复制完成 ($([math]::Round($sw.Elapsed.TotalSeconds))s)"

# ── 3. 验证关键文件 ──
Write-Output "`n[2/4] 验证关键文件..."
$checks = @(
    'ecos_backend\pom.xml',
    'ecos_backend\gateway\pom.xml',
    'ecos_frontend\package.json',
    'ecos_frontend\server.ts',
    'ecos_frontend\vite.config.ts',
    'ecos-docker\docker-compose.yml',
    'AGENTS.md'
)
$allOk = $true
foreach ($f in $checks) {
    $p = Join-Path $DST $f
    if (Test-Path $p) {
        Write-Output "  [OK] $f"
    } else {
        Write-Output "  [MISS] $f"
        $allOk = $false
    }
}
if (!$allOk) {
    Write-Output "`n[ERROR] 关键文件缺失，请检查复制完整性。"
    exit 1
}

# ── 4. 统计 ──
Write-Output "`n[3/4] 文件统计..."
$cnt = (Get-ChildItem $DST -Recurse -File | Measure-Object).Count
$sz = (Get-ChildItem $DST -Recurse -File | Measure-Object -Property Length -Sum).Sum
Write-Output "  文件数: $cnt"
Write-Output "  总大小: {0:N1} MB" -f ($sz / 1MB)

# ── 5. 完成 ──
Write-Output "`n[4/4] 配置说明..."
Write-Output @'

══════════════════════════════════════════════════════════
  迁移完成!  下一步操作:
══════════════════════════════════════════════════════════

  1. 安装前端依赖:
     cd D:\workspace\javaprojects\ECOS\ecos_frontend
     npm install

  2. 启动前端 (BFF Express + Vite, 端口 3000):
     cd D:\workspace\javaprojects\ECOS\ecos_frontend
     npx tsx server.ts
     → 浏览器打开 http://localhost:3000

  3. 编译后端 (标准版):
     cd D:\workspace\javaprojects\ECOS\ecos_backend
     mvn clean install -DskipTests -Dmaven.test.skip=true -q

  4. 启动 Gateway (端口 8080):
     cd D:\workspace\javaprojects\ECOS\ecos_backend\gateway
     mvn spring-boot:run
     或打包后用 java -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar

  5. (可选) Docker 基础设施:
     cd D:\workspace\javaprojects\ECOS\ecos-docker
     docker-compose up -d

══════════════════════════════════════════════════════════
  环境变量参考 (若 Java 路径不全, 可加系统变量):
    JAVA_HOME = C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot
    PATH      = %JAVA_HOME%\bin;D:\JavaProjects\env\apache-maven-3.9.11\bin;%Path%
══════════════════════════════════════════════════════════
'@
