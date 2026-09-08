#!/usr/bin/env python3
"""Wave 2: 归档 Wave 1 + 派发后端收束 swarm"""
import subprocess, json, sys

board = "ecos"

# 1. 归档 Wave 1 root
for tid in ["t_817ac17a", "t_6c638d9e", "t_cb5c8f39", "t_6fcd1f07"]:
    r = subprocess.run(["hermes", "kanban", "--board", board, "archive", tid],
                       capture_output=True, text=True, timeout=20)
    print(f"archive {tid}: rc={r.returncode} {r.stderr[:80]}")

# 2. 准备 Worker — unblock 旧 worker 无用，派发新 swarm
wave2_goal = """# PMO-46: 数据源多类型后端收束 (Wave 2)

> 架构铁律: ECOS §0.2/§0.4/§2.5 | task_id: ecos-datasource-w2
> 前置: Wave 1 已产出 PMO45DataSourceController + DataSourceServiceImpl 骨架，但 budget 耗尽中断
> 铁律: 基础设施访问走 runtime-access；密码加密走 security-engine；不加新 Maven 模块

## 本次只做后端，改动范围收窄到：

### 1. DataSourceBaseVO / DataSourceConfigVO 类型枚举
- 在 DataSourceBaseVO 扩展 type 字段枚举：ORACLE, MSSQL, DM, KINGBASE, GAUSS, MINIO, FILESYSTEM
- DataSourceConfigVO 增加 Map<String,String> typeSpecificParams 字段

### 2. 驱动依赖 runtime-access/pom.xml
- 添加 ojdbc8 (23.3.0.23.09), mssql-jdbc (12.4.2), DmJdbcDriver18 (8.1.3.140), kingbase8 (8.6.0), opengauss-jdbc (5.1.0-2)
- 全部 <optional>true，不膨胀主 JAR

### 3. JdbcConnectionTester (新建 data-engine/impl/.../connector/)
- 方法 connectionTest(DataSourceConfigVO config)
- try { Class.forName(driverClass) } -> Class.forName Unsupported 提示"驱动未加载"
- 按 type 拼 JDBC URL -> JDBC DriverManager.connect
- MinIO type=MINIO 时走 MinioClient 连通（仅 enterprise/ultimate profile 生效）
- FILESYSTEM 走 Files.exists(basePath) + checkAccess
- **.password 从 config 明文读入（Controller 层已有 security-engine decrypt 机制）**
- 超时: socketTimeout=10s

### 4. MinIO 版本控制
- createDataSource 方法及 previewSchema 方法入口检测：
  - if type==MINIO && spring.profiles.active in (standard) -> 抛 ValidationException("仅 enterprise/ultimate 版本支持 MinIO")
  - 其他环境放行

### 5. 三滤波器（之前 Wave 1 已改过，但确认下）
- VersionPrefixRewriteFilter: /api/v1/datasource -> /api/datasource
- SecurityConfig: permitAll /api/v1/datasource/**
- ClearanceInterceptor: 豁免列表加 /api/v1/datasource
- 如果之前改过，做 double-check；如果之前漏改，补上

### 6. PMO45DataSourceController
已有的 controller 保留，确认：
- POST /api/v1/datasource/test-connection/{id} -> 读 DB 配置 + decrype -> 调 tester
- POST /api/v1/datasource/preview-schema -> body config 校验 -> 调 tester
- 安全: 所有敏感字段经 security-engine encrypt/decrypt（先 grep 确认已有调用点）

### 7. 避免误改
以下文件不需要动，禁止修改：
- AgentMetricsService.java / OntologyWorkflowController.java / UserController.java
- 这些文件在 Wave 1 误改，你应该底色回和干净的 HEAD 一致（但绝对不要 git checkout -- 强回，只保证你没有额外修改它们）

### 暂时不做 (交给 Wave 3 前端)
- 前端 9 个 ConfigForm（不建 tsx）
- 前端图标映射
- 前端 i18n（但 backend 的 i18n 如果有的话可以加）

### 验收 (在 WSL 里)
1. mvn -pl data-engine/data-engine-impl -am install -DskipTests -q 通过
2. curl 测试 (admin/admin123 登录后):
   - POST /api/v1/datasource type=ORACLE config=[{host:127.0.0.1,port:1521,serviceName:ORCL,username:system,password:x}] -> 201
   - POST /api/v1/datasource type=MINIO (标准版) -> 400
   - POST /api/v1/datasource/test-connection/{id} -> 200 {success:false,error:"...oraracle ora mysql jdbc..."} (能清楚显示哪种连接没成功，而不是超时或异常裸报 500)

### 禁止事项
1. 不加新 Maven 模块
2. 不自建加密 (必须走 security-engine)
3. 不修改 Wave 1 已无法改动的文件（AgentMetricsService 等）
4. 三滤波器必须双路径注册 (/api/v1/xxx + /api/xxx)
"""

cmd = [
    "hermes", "kanban", "--board", board, "swarm",
    "--worker", "ecos-be:数据源后端收束Wave2",
    "--verifier", "ecos-fe",
    "--synthesizer", "ecos-pm",
    "--created-by", "ecos-pm",
    "--json",
    wave2_goal,
]

r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
print("\n=== SWARM WAVE 2 ===")
print("RC:", r.returncode)
print(r.stdout)
if r.returncode != 0:
    print("STDERR:", r.stderr[:500])
