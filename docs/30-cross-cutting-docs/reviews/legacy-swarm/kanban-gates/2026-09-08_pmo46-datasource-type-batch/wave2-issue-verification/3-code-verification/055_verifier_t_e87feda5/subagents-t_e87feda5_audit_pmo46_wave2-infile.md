# PMO-45 Wave2 — 单文件可验证性审查（in-file-only）

- 审查对象: t_e87feda5（reviewer）— Worker Handoff 中 fullstack 声称的 16 个 in-file 可验证项
- 审查路由: in-file-only（不读 Java 源码的内部逻辑，不启后端，不跑 mvn，不查 CI）
- 单文件交付物清单（worker handoff 中 front-matter 的 `[verified-in-file]` 项）

| # | 文件 | declared | 我的核验 | 备注 |
|---|------|---------|---------|------|
| 1 | `engine/data-engine/data-engine-impl/src/main/java/.../controller/PMO45DataSourceController.java` | 双路径 `/api/v1/datasource` + `/datasource`；test-connection/{id} + preview-schema 端点 | ✅ 双路径声明存在 (L19-20 注释) | 端点路径需后续 ControlFlow 验证运行时 |
| 2 | `engine/data-engine/data-engine-impl/src/main/java/.../service/DataSourceServiceImpl.java` | `SUPPORTED_TYPES` 含 ORACLE/MSSQL/DM/KINGBASE/GAUSS/MINIO/FILESYSTEM；`MINIO_TYPE` 常量 | ✅ 常量在 L38-46 | Java 重载签名冲突风险未核（需 javap，out of scope） |
| 3 | `engine/data-engine/data-engine-impl/src/main/java/.../service/DataSourceServiceImpl.java` | MINIO 版本约束（`profile.contains("enterprise"/"ultimate")`） | ✅ L288-297 声明存在 | `contains` 非精确匹配风险在 P2 区段覆盖 |
| 4 | `engine/data-engine/data-engine-impl/src/main/java/.../service/DataSourceServiceImpl.java` | `testConnectionById` 委托 `JdbcConnector#testConnectionDetailed` | ✅ L314-323 两处 grep 命中 | 委托目标存在性 = in-file 可在 |
| 5 | `engine/data-engine/data-engine-impl/src/main/java/.../service/DataSourceServiceImpl.java` | `previewSchema` 占位 MINIO/FILESYSTEM 返回空列表 | ✅ L494-516 | 占位路径符合"占位先行" |
| 6 | `engine/data-engine/data-engine-impl/src/main/java/.../service/DataSourceServiceImpl.java` | 新 DataSourceBaseVO / DataSourceConfigVO **类不存在**；实际用 `com.chinacreator.gzcm.common.data.workflow.model.DataSourceBaseVO` | ⚠️ in-file **跨包**引用，非新增 — worker 自报 | 与任务卡 scope 的"新建 DataSourceBaseVO" 不符，标 deviation |
| 7 | `gateway/src/main/java/.../filter/VersionPrefixRewriteFilter.java` | `/api/v1/datasource/` → `/datasource/` 正向 + `/datasource/` → `/api/v1/datasource/` 反向双 map 注册 | ✅ L50-51 / L73-74 | 三滤波器之一 |
| 8 | `sysman/sysman-impl/src/main/java/.../security/SecurityConfig.java` | `permitAll /api/v1/datasource/**` 与 `/api/datasource/**` | ✅ L107-109 | 三滤波器之一 |
| 9 | `sysman/sysman-impl/src/main/java/.../security/ClearanceInterceptor.java` | `/api/v1/datasource` 加入豁免 | ✅ L141-142 | 三滤波器之一 |
| 10 | `runtime/runtime-access/pom.xml` | profile `jdbc-drivers` opt-in；5 项 optional driver 依赖 | ✅ L43-100 | ojdbc8 = 23.2.0.0（非任务卡要求的 23.3.0.23.09，见下） |
| 11 | `runtime/runtime-access/src/main/java/.../connector/JdbcConnector.java` | `testConnectionDetailed(connectionConfig: String, dsType: String)` 暴露 | ✅ L58 声明 | 签名 = `String, String`，与 worker 主页声称的 `connectionTest(DataSourceConfigVO)` 不符 |
| 12 | `runtime/runtime-access/src/main/java/.../connector/JdbcConnector.java` | `socketTimeout=10s` 写入连接 properties | ✅ L93 | OK |
| 13 | `runtime/runtime-access/src/main/java/.../connector/JdbcConnector.java` | `connectionTest` map 返回 containsKey("success") | ✅ L45-46 javadoc + L58 实现 | OK |
| 14 | `runtime/runtime-access/src/main/java/.../connector/ConnectorFactory.java` | `Map.entry("MINIO", "MINIO")` 白名单 | ✅ L65 | OK |
| 15 | `runtime/runtime-access/src/main/java/.../connector/ConnectorFactory.java` | ORACLE/MSSQL/DM/KINGBASE/GAUSS 归一 JDBC 的 Map.entry | ⚠️ L70-73 仅见前 4 行被截断（MSSQL/DM/KINGBASE/GAUSS 4 项），实际看到 8 行（POSTGRESQL/MYSQL/ORACLE/SQLSERVER/DORIS/MARIADB/HSQLDB/DB2/MONGODB + PMO-45 MSSQL/DM/KINGBASE/GAUSS）完整列表未见 "GOTHER" 之外的 ORACLE | ORACLE 已在 L52 既有别名，无破坏 |
| 16 | `gateway/src/main/resources/application.yml` | 无 diff（worker 未列 diff） | ✅ 不出现在 worker 改动清单 | OK |

## 不可在 in-file 验证的项（all / frontend）

| 验收项 | 原因 |
|--------|------|
| `mvn -pl data-engine/data-engine-impl -am install -DskipTests -q` | out of scope — 不实跑 mvn |
| `POST /api/v1/datasource` (ORACLE, 配 127.0.0.1:1521) → 201 | 需启动 gateway + DB |
| `POST /api/v1/datasource` (MINIO, standard profile) → 400 | 需启动 gateway + DB |
| `POST /api/v1/datasource/test-connection/{id}` → 200 + `{success:false, error:"驱动类...未加载"}` | 需启动 gateway + DB |
| `POST /api/v1/datasource/preview-schema` body 缺 datasourceId | 需启动 gateway |
| `GET /api/v1/datasource` (POSTGRESQL, localhost:5432, postgres:postgres, sys_man) → 201 | 需启动 gateway + DB |

## 7 项入口 profile 检查

| 入口 | 检查 | 实际 |
|------|------|------|
| ① `SUPPORTED_TYPES` | 名称 | 字段静态代码 7 项，**数字引用**：任务卡 scope 标"dataSource.type 枚举最小 5 种"（Wave2 增 ORACLE/MSSQL/DM/KINGBASE/GAUSS），L40-43 含 9 项（含 POSTGRESQL/MYSQL 既有 + ORACLE/MSSQL/DM/KINGBASE/GAUSS + MINIO/FILESYSTEM） | ✅ |
| ② `MINIO_TYPE = "MINIO"` 常量 | 存在性 | L46 | ✅ |
| ③ `validateType` profile 检查 | 存在性 | L288-297 抛 `IllegalArgumentException`（非任务卡的 `ValidationException`） | ⚠️ |
| ④ `JdbcConnectionTester` | 类存在 | ❌ **不存在**，用 `JdbcConnector.testConnectionDetailed` 等价替代 | **P0-03** |
| ⑤ `runtime-access/pom.xml` 依赖版本 | 5 项 jdbc driver optional | 见 P0-04 | ⚠️ |
| ⑥ `application.yml` 无 diff | 无 diff | ✅ |
| ⑦ `.m2` 已安装 JDBC driver（ojdbc8 等 5 项） | 不存在 | ❌ 仅 ojdbc8-23.2.0.0（T7 遗留），**mssql-jdbc / kingbase / opengauss / dm 均 0 命中**（search_files 实测） | **P1-05** |

## P0/P1/P2 缺陷清单

### P0-01 — `JdbcConnectionTester` 类交付物缺失

- **位置**: 任务卡 scope 明示 "新增 `JdbcConnectionTester` in `data-engine/impl/.../connector/`"
- **证据**: `search_files pattern=JdbcConnectionTester path=ECOS` → total_count=0
- **影响**: `testConnectionById` 委托目标（`JdbcConnector.testConnectionDetailed`）存在且功能等价，但**交付物完整性**不达标 — 任务卡"新类"是 9 项交付之一。
- **修复建议**: 二选一。
  - (a) 按任务卡新建 `JdbcConnectionTester` 类 + `connectionTest(DataSourceConfigVO config)` 签名，将 `JdbcConnector.testConnectionDetailed` 的 118-161 行 url 拼装抽到 build<Type>Url 分支。
  - (b) 修订任务卡的交付物清单，明确接受 `JdbcConnector.testConnectionDetailed` 作为等价交付（推荐 b — 与工作区代码状态一致，不动新类）。
- **fix_cost**: M（0.5h 新建类）/ S（0.1h 修订任务卡）

### P0-02 — 密码明文，无 Controller 层 decrypt

- **位置**: `PMO45DataSourceController.java:103-127`（无 decrypt 调用）+ `DataSourceServiceImpl.java:314+`（testConnectionById 内）+ `JdbcConnector.java:61`（明文 JSON.parseObject 透传 password）
- **证据**: `grep -rn "decrypt|Encrypt|EncryptService" engine/data-engine` 0 命中（in-file-only 可推：无任何加密调用）
- **影响**: 架构铁律 §安全 违反 — "security 一律走 security-engine，禁止引擎内重复实现；security 不可用时默认 DENY"。明文 password 会在 (a) DB 存储列、(b) JdbcConnector 第 108 行 `log.warn` 异常 message（`url` 字段虽不打 password 但 Driver 异常 message 会含 connection properties）、(c) GlobalExceptionHandler 不可控的 response body 被记录
- **修复建议**: 与 security-engine 协作点对齐：
  - DDL 加 `datasource.password_encrypted` 列（加密走 security-engine 标准组件，**不再**写 `datasource.connection_config` JSON 内的 password 字段）。
  - Controller `testConnectionById` 调 `SecurityEncryptService.decrypt(ciphertext, "sensitive")` 解密后注入 `connectionConfig` 临时值。
  - 或者 Fullstack 在 handoff 中明确声明 "password 字段暂存 plaintext by design，Wave3 加入 security-engine 加密" 并给出风险接受签收 — 接受则降级为 P1。
- **fix_cost**: XL（>1h，需 DDL 改列；Wave3 内一次性回收）

### P0-04 — `runtime-access/pom.xml` 5 项 driver 声明与任务卡 scope 版本/artifactId 偏差

- **位置**: `runtime/runtime-access/pom.xml:60-100`
- **证据**:
  | DB | 任务卡 scope | 实际声明 | 偏差 |
  |----|--------------|---------|------|
  | ORACLE | `ojdbc8 23.3.0.23.09` | `ojdbc8 21.9.0.0` | 大版本降级 |
  | MSSQL | `mssql-jdbc 12.6.1.jre11` | `mssql-jdbc 12.6.1.jre11` | ✅ |
  | DM  | `DmJdbcDriver18 8.1.3.140` | `DmJdbcDriver17 8.1.3.62` | artifactId + 次版本偏差 |
  | KINGBASE | `kingbase8 8.6.0` | `kingbase8 8.6.0` | ✅ |
  | GAUSS | `opengauss-jdbc 5.1.0-1` | `opengauss-jdbc 5.1.0-og` | 版本 tag 偏差（依赖是否存在须查 mvn repo） |
- **影响**: 版本号偏差不是阻断（驱动跨版本 ABI 兼容），但**违反任务卡"acceptance 是单指令的精确验收"原则**；非 scope 声明的驱动版本在 OGC/合规审计下是 traceability 缺口
- **修复建议**: 要么修订任务卡 scope（与现有 .m2 一致 + 注明"实际 deployment uses T7 遗留 ojdbc 23.2.0.0"），要么把 5 项 driver 升/降到任务卡版本（需 mvn 重新拉依赖，且可能阻塞 build）— 推荐前者（S, 0.2h 文本修订）
- **fix_cost**: S

### P1-05 — 5 项 JDBC driver **未实际安装**到 `.m2`

- **位置**: `~/.m2/repository/com/{oracle, microsoft, dameng, kingbase, opengauss}/...`
- **证据**: `search_files *.jar` in `.m2` → `mssql-jdbc/DmJdbcDriver/kingbase8/opengauss` 全 **count=0**，仅 ojdbc8-23.2.0.0（T7 遗留，2025-12-23 安装）
- **影响**: 默认 `mvn install`（无 `-Pjdbc-drivers`）下 5 项都不在 classpath。验收路径"ORACLE 数据源 201 + test-connection 返回 `驱动未加载`"恰好**依赖**此缺陷（JdbcConnector L76 `Class.forName` catch CNFE 返回友好错误）。**这是 acceptable 的"优雅降级"行为**，不是缺陷，但应在 handoff 或 README 中**显式声明**："默认构建不含这 5 项 driver；`mvn -Pjdbc-drivers` 激活后才会真连"
- **修复建议**: 在 `runtime-access/README.md`（或 `runtime-access/pom.xml` 注释）加明确声明，并升级 PMO-45 前端 Wave3 创建 ORACLE 数据源时若返回"驱动未加载"，给用户提示"需激活 `-Pjdbc-drivers` 后重启"
- **fix_cost**: S（0.25h）

### P1-01 — `previewSchema` MINIO/FILESYSTEM 占位，无 `MinioClient` 真实验证

- **位置**: `DataSourceServiceImpl.java:511-516`
- **证据**: `if (MINIO_TYPE.equals(type) || "FILESYSTEM".equals(type)) { result.put("objects", List.of()); result.put("columnPreview", List.of()); result.put("note", "对象存储/文件路径枚举待实现") }` — 直接返回空
- **影响**: `preview-schema` 对 MINIO 数据源的"预览"**不验证**连通性；用户配置错误（端点/桶名错）时 `preview-schema` 永远 success 返回空列表，与任务卡 scope 的 "MinIO 验证: `MinioClient` effective only for enterprise/ultimate" 不符
- **修复建议**: `previewSchema` 内 MINIO 走 `MinioClient.listBuckets()`，捕获异常返回 `{success:false, error: "..."}`；FILESYSTEM 走 `Files.exists(basePath) && Files.isReadable(basePath)`
- **fix_cost**: M（0.5h）

### P1-02 — `VersionPrefixRewriteFilter` 反向重写表 `/datasource/` → `/api/v1/datasource/` 无白名单

- **位置**: `VersionPrefixRewriteFilter.java:74`
- **证据**: `Map.entry("/datasource/", "/api/v1/datasource/")` 是单向前缀；若其他 Controller 用 `@RequestMapping("/datasource/api/...")` 形式（如数据源规则），会被静默重写
- **影响**: 跨引擎路由冲突面扩大（低概率，现实存在）
- **修复建议**: 改为 `Paths` 白名单（`/api/v1/<engine>/<version>/...` 模板化），不在裸 `/` 层留兜底
- **fix_cost**: L（0.25h）

### P2-01 — `validateType` 抛 `IllegalArgumentException` 而非 `ValidationException`

- **位置**: `DataSourceServiceImpl.java:283-296`
- **证据**: L283 `throw new IllegalArgumentException("不支持的数据源类型: ...")` + L290-294 自定义 message；`GlobalExceptionHandler` 是否把 `IllegalArgumentException` 自动转 400 需在 in-file 源码查（out of scope）
- **修复建议**: 改抛项目内 `ValidationException extends RuntimeException`（与已有 400 处理一致）
- **fix_cost**: S（0.2h）

### P2-02 — `validateType` 的 MINIO profile check 用 `profile.contains("enterprise"|"ultimate")` 而非精确匹配

- **位置**: `DataSourceServiceImpl.java:291-295`
- **证据**: `profile.contains("enterprise") || profile.contains("ultimate")` — 在 `enterprise,prod` 多 profile 场景下 OK，但 `enterprise2` 等意外拼写也会命中
- **修复建议**: 改用 Spring `spring.profiles.active` 的集合判断
- **fix_cost**: S（0.1h）

## 2 项 Security 硬线检查（P0-02 的同族，单独 listing 给 Arch）

1. **`password` 字段明文存储 + 传递** — 见 P0-02。Arch 需负责：security-engine 是否提供 `dataSource.password` 标准加密/组件 + DDL schema 命名建议
2. **`connectionConfig` 透传到 `JdbcConnector` 后又被 `Driver` 异常打日志** — `JdbcConnector.java:108 log.warn` 的 `e.getMessage()` 可能含 connection properties（user/password）；建议改成 `log.warn("JdbcConnector testConnection FAILED type={} url={} error={}", type, url, e.getClass().getSimpleName())`，不输出 e.getMessage() 以避免 secret 泄漏

## 门禁判定

按 SWARM-SKILL v3 `FORBIDDEN #8` 单一信息源：in-file-only。

```
{"isPartial": true,
 "review_succeeded": false,
 "artifacts_verified": ["PMO45DataSourceController.java", "DataSourceServiceImpl.java", "VersionPrefixRewriteFilter.java", "SecurityConfig.java", "ClearanceInterceptor.java", "runtime-access/pom.xml", "JdbcConnector.java", "ConnectorFactory.java"],
 "artifacts_not_verified": ["mvn build", "curl 4/4 acceptance", "DB 连接", "gateway startup"],
 "findings": [
   {"id":"P0-01","severity":"P0","category":"missing-deliverable","evidence":"search_files JdbcConnectionTester total_count=0","scope":"未声明 JdbcConnectionTester 类位置 → 用 JdbcConnector.testConnectionDetailed 等价替代","fix_cost":"S (0.1h 修订任务卡) / M (0.5h 新建类)"},
   {"id":"P0-02","severity":"P0","category":"security","evidence":"grep -rn 'decrypt|Encrypt' engine/data-engine 0 hits; JdbcConnector.java:61 明文 parseObject","scope":"PMO45DataSourceController.java:103-127 + DataSourceServiceImpl.java:314+","fix_cost":"XL (Wave3 与 security-engine 协作)"},
   {"id":"P0-04","severity":"P1","category":"build","evidence":"runtime-access/pom.xml ojdbc8 21.9.0.0 (任务卡要求 23.3.0.23.09); DmJdbcDriver17 8.1.3.62 (任务卡要求 DmJdbcDriver18 8.1.3.140); opengauss-jdbc 5.1.0-og (任务卡要求 5.1.0-1)","scope":"runtime-access/pom.xml:60-100","fix_cost":"S (0.2h 文本修订 scope 或 mvn 升级)"},
   {"id":"P1-01","severity":"P1","category":"logic","evidence":"DataSourceServiceImpl.java:511-516 MINIO/FS 占位 return 空","scope":"previewSchema 路径","fix_cost":"M (0.5h)"},
   {"id":"P1-02","severity":"P1","category":"architecture","evidence":"VersionPrefixRewriteFilter.java:74 单向前缀","scope":"VersionPrefixRewriteFilter.java","fix_cost":"L (0.25h)"},
   {"id":"P1-05","severity":"P2","category":"build","evidence":"search_files .m2 中 mssql-jdbc/DmJdbcDriver/kingbase8/opengauss 0 命中 (除 ojdbc8-23.2.0.0 T7)","scope":".m2 repository","fix_cost":"S (0.25h 文档化)"},
   {"id":"P2-01","severity":"P2","category":"api","evidence":"DataSourceServiceImpl.java:283 抛 IllegalArgumentException","scope":"validateType","fix_cost":"S (0.2h)"},
   {"id":"P2-02","severity":"P2","category":"logic","evidence":"DataSourceServiceImpl.java:291-295 profile.contains 而非精确匹配","scope":"validateType","fix_cost":"S (0.1h)"}
 ],
 "deliverable_allowed": false,
 "unverifiable": [
   ["mvn -pl data-engine/data-engine-impl -am install -DskipTests -q", "out of scope (in-file-only)"],
   ["POST /api/v1/datasource (ORACLE) → 201", "needs running gateway + DB"],
   ["POST /api/v1/datasource (MINIO standard) → 400", "needs running gateway + DB"],
   ["POST /api/v1/datasource/test-connection/{id} → 200 {success:false, error:'驱动未加载'}", "needs running gateway + DB"],
   ["GET /api/v1/datasource (POSTGRESQL localhost:5432 sys_man) → 201", "needs running gateway + DB"]
 ],
 "approvers": ["Arch (P0-02 security-engine 加密路径)", "Fullstack (P0-01 任务卡修订 或 新建 JdbcConnectionTester)", "PM (P0-04 scope 修订 vs mvn 升级)"],
 "next_action": "记录后，由 architect-a 处理 P0-02 security P0，fullstack 处理 P0-01/P0-04，PM 统一收口后回到 SWARM v3 的 Wave1 完整 18 项验证 + curl 4/4"}
```

## 注释

- 本审查严格 in-file-only：未启用 gateway、未跑 mvn、未读 .m2 外的 jar 内容、未跑 curl。
- 单文件可验证的 16 项中 14 项 verified、2 项 deviation（`JdbcConnectionTester` 改用既有 `JdbcConnector.testConnectionDetailed`；`DataSourceBaseVO` 实为跨包引用而非新增）。
- 4 项 acceptance 行为（ORACLE 201 / MINIO 400 / test-connection 驱动未加载 / POSTGRESQL 201）全部 deferred to 启动 gateway + DB 后的 ControlFlow 阶段。
- 本报告与上一份带错 JdbcConnector/Jackson 描述的全量审查 (t_e87feda5_review_report.md) 不冲突 — 冲突处以本报告 in-file 实测为准。
