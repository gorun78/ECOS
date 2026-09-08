#!/usr/bin/env python3
"""Wave 3: 前端 9 个 ConfigForm + Modal + i18n + 图标"""
import subprocess, json

board = "ecos"

wave3_goal = """# PMO-47: 数据源前端多类型 CRUD (Wave 3)

> 架构铁律: 前端 4.1 主题铁律 + 4.2 lucide + 4.3 i18n + 4.6 组件铁律
> task_id: ecos-datasource-w3
> 前置: Wave 2 后端已交付 9 种类型 CRUD + test-connection + preview-schema API
> 禁止: 硬编码颜色、硬编码中文、自定义 SVG

## 1. types.ts 类型扩展

在 src/pages/data-workbench/types.ts 里：
- DataConnection.type: PostgreSQL | MySQL | Oracle | MSSQL | DM | Kingbase | GaussDB | MinIO | Filesystem
- 新增 DataSourceTypeConfig = { host, port, username, password, dbName?, typeSpecific: Record<string, string|number|boolean> }
- 类型→默认端口/驱动/图标 常量表

## 2. 7 个类型专属 ConfigForm 组件（放 data-workbench/components/）

每家一个组件，只聚焦**该类型特有参数**，通用 host/port/username/password 由外层 Modal 处理：
- OracleConfigForm.tsx: serviceName / urlFormat(JID/JDBC/EZCONNECT) / connectionTimeout(s) / statementCacheSize
- MssqlConfigForm.tsx: encrypt(toggle) / trustServerCertificate(toggle) / applicationName / multiSubnetFailover(toggle)
- DmConfigForm.tsx: compatibleMode(MySQL/Oracle) / loginEncrypt(toggle) / passwordEncryptMode(0/1/2/3)
- KingbaseConfigForm.tsx: database / tablecase/compatibleMode
- GaussConfigForm.tsx: sslMode / compression(toggle) / readOnly(toggle) / applicationName
- MinioConfigForm.tsx: bucket / region / pathStyleAccess(toggle) / **standard 版本时表单顶部弹窗警告条 + save 时 disabled**
- FilesystemConfigForm.tsx: basePath / readOnly(toggle) / maxFileSizeMB(number) / allowedExtensions(multiselect)

### 铁律
- 每个组件均用 useTheme() + useLanguage()；禁止 bg-white / text-blue-500 硬编码
- 图标 lucide-react (Settings, Database, Cloud, Shuffle, HardDrive 等)
- 每个文件 < 200 行; 超限则拆出 field 子组件
- 所有控件加 required/label/说明 (i18n 末尾 help 段)

## 3. DataSourceModal.tsx (已有)

改造：
- type select 切换时 render 对应 ConfigForm
- 底部 <button onClick={testConnection}> 调 POST /api/v1/datasource/preview-schema，返回 200 显示成功/失败弹窗
- 保存时 payload 打平 config + typeSpecificParams 作为持久化后端对象

## 4. 数据源列表 (ConnectionsTab 或DBC 정상Columns那里)

- 类型图标映射 (不用 DWxxx 兼容现有 lucide-based icons)
  - PostgreSQL: Database (现有)
  - MySQL: Database (变体 color)
  - Oracle: 类似 Database 但 different accent (default 调色; 用 DB-red 主题变量 style)
  - MSSQL: DatabaseBlue
  - DM: DatabaseCyan
  - Kingbase: DatabaseGreen
  - GaussDB: DatabaseNeutral
  - MinIO: Cloud
  - Filesystem: FolderIcon

- type 列展示 t('dw.types.' + type.toLowerCase()) （i18n）

## 5. i18n: dw/zh-CN.json + dw/en.json

### 新增 keys
```
"dw.types.oracle": "Oracle"           | "Oracle"
"dw.types.mssql": "MSSQL"             | "MSSQL"
"dw.types.dm": "达梦数据库"                | "Dameng"
"dw.types.kingbase": "人大金仓"             | "KingbaseES"
"dw.types.gauss": "GaussDB(高斯)"         | "GaussDB"
"dw.types.minio": "MinIO 对象存储"         | "MinIO S3"
"dw.types.filesystem": "本地文件系统"        | "Filesystem"

"dw.oracle.serviceName": "服务名"      | "Service Name"
"dw.oracle.urlFormat": "URL 格式"      | "URL Format"
"dw.oracle.connectionTimeout": "连接超时(秒)" | "Connection Timeout (s)"
"dw.oracle.statementCacheSize": "陈述缓存大小" | "Statement Cache Size"

"dw.mssql.encrypt": "加密连接"          | "Encrypt"
"dw.mssql.trustCert": "信任服务器证书"     | "Trust Server Cert"
"dw.mssql.appName": "应用名称"          | "Application Name"
"dw.mssql.multiSubnet": "多子网故障切换"    | "Multi-Subnet Failover"

"dw.dm.compatMode": "兼容模式"           | "Compat Mode"
"dw.dm.loginEncrypt": "登录加密"         | "Login Encrypt"
"dw.dm.pwdEncMode": "密码加密方式"        | "Password Enc Mode"

"dw.kingbase.database": "数据库"         | "Database"
"dw.kingbase.tableCase": "表名大小写"       | "Table Case"
"dw.kingbase.compatMode": "兼容模式"       | "Compat Mode"

"dw.gauss.sslMode": "SSL 模式"         | "SSL Mode"
"dw.gauss.compression": "压缩"          | "Compression"
"dw.gauss.readOnly": "只读"            | "Read-Only"
"dw.gauss.appName": "应用名称"          | "Application Name"

"dw.minio.bucket": "桶名"              | "Bucket"
"dw.minio.region": "区域"              | "Region"
"dw.minio.pathStyle": "路径风格"        | "Path-style"
"dw.minio.notSupported": "当前版本不支持 MinIO（需 enterprise 或 ultimate）" | "MinIO only available in enterprise/ultimate"

"dw.fs.basePath": "基础路径"             | "Base Path"
"dw.fs.readOnly": "只读"                | "Read-only"
"dw.fs.maxSizeMB": "单文件最大大小 (MB)"   | "Max File Size (MB)"
"dw.fs.extensions": "允许扩展名"         | "Allowed Extensions"
```

## 6. 三效验收 (在 running 前端页面)

1. 登录 admin/admin123 → 数据工作台 → 数据源与物理连接
2. 点「新建」 → 类型切 Oracle → 表单出现 serviceName / urlFormat / connectionTimeout / statementCacheSize
3. 类型切 MSSQL → 表单出现 encrypt / trustCert / appName / multiSubnet (toggle 组件)
4. 类型切 MINIO (standard) → 顶部 warning 条显示 "当前版本不支持 MinIO" + save 按钮禁用
5. 类型切 FILESYSTEM → basePath + readOnly toggle + allowedExtensions 多选
6. 每个类型 form 提交时 payload.config.typeSpecificParams 带对应 key

### 禁止
- 不硬编码中文 (i18n)
- 不硬编码颜色 (useTheme)
- 模态内列表展开按钮保持上次的 pointer-events 修复 (T3-1 已修，别覆写)

"""

cmd = [
    "hermes", "kanban", "--board", board, "swarm",
    "--worker", "ecos-be:数据源前端多类型Wave3",
    "--verifier", "ecos-fe",
    "--synthesizer", "ecos-pm",
    "--created-by", "ecos-pm",
    "--json",
    wave3_goal,
]
r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
print("RC:", r.returncode)
print(r.stdout)
if r.returncode != 0:
    print("STDERR:", r.stderr[:500])
