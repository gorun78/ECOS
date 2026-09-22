# PMO-45 Wave2 数据源多类型后端收拢 — Reviewer 审查报告

- 审查对象: t_1ab054fb（Fullstack worker handoff）
- 审查路由: 专项 = code-review + arch-consistency 交叉核对，contrast = T7 23.2.0.0 (oracle) vs T8 ojdbc8 23.2.0.0 升版
- 审查基线: PMO-45 Wave2 任务卡 scope（7 项交付）+ 5 条禁止动作
- 审查时间: 2026-09-08T13:22:00+08:00
- 审查者: verifier t_e87feda5
- deliverable_allowed: **false**（FAIL — 1 P0 + 4 P1）
- isPartial: true（P1-03 security-engine 加密路径需 Fullstack 架构决策澄清后才能关闭）
- review_elapsed_s: 1798

---

## 1. 结论与可修复性

P0-01 `JdbcConnectionTester` 类**完全缺失**、P0-02 `DataSourceBaseVO/DataSourceConfigVO` 类**不存在**（实际数据模型是 `DataSourceEntity`），导致任务卡中两项"新类"交付物**未实现**，且 actual 工程路径不匹配。P1-01 密码明文落盘 `connectionConfig`，无 Controller 层解密（违反任务卡明文"密码：Controller 层从 DB 读出后明文取用（加密走 security-engine）"声明）。P1-02 `datasource/preview-schema` 路径同样以 `ConnectionConfigType` 直接走强类型反序列化，password 明文在反序列化链路上反复流转，缺少密钥边界；P1-03 `testConnectionById` 对 `MINIO`/`FILESYSTEM` 的"非 JDBC 占位"路径（`JdbcConnector` case "MINIO"/case "FILESYSTEM" 是否存在）尚不确认（前文 `JdbcConnector.java` 仅读到 `switch(type)` 上方部分，未见 MINIO/FILESYSTEM 的特殊分支，需读取 `JdbcConnector.java` 141–402 区间确认；若连这两个 case 也未实现，则 `preview-schema` 对 MINIO 会沿 `getConnector(MINIO)` 走 `"MINIO"` bean，但 ConnectorFactory 白名单 MINIO→bean "MINIO" 且 List<Connector> 中没有 MINIO bean 时抛 `IllegalArgumentException`）。这些 P0/P1 中有部分可在 5 分钟内由 Fullstack 通过补 `JdbcConnectionTester` 类 + Controller URL decoding 修复。**不可只写报告**：P0-01/P0-02 是"交付物未落地"，不是漏项，是完备性缺陷，不放行。

## 2. 逐节核对（acceptance 对照）

| # | 验证项 | 声称 | 实际 | 证据 | 结论 |
|---|--------|------|------|------|------|
| 1 | 构建 | `mvn -pl data-engine/data-engine-impl -am install -DskipTests -q` 通过；`data-engine-impl-1.0.0-SNAPSHOT.jar` 已 install | 同左 | `~/.m2/repository/com/chinacreator/gzcm/data-engine-impl/1.0.0-SNAPSHOT/data-engine-impl-1.0.0-SNAPSHOT.jar` (485 KB, 13:11:38, 与 `DataSourceServiceImpl.java` mtime 12:55:43 同批次) | ✅ verified |
| 2 | 驱动依赖 | 5 项 JDBC 驱动 conditional；ojdbc8 升到 23.2.0.0 | 路径 `runtime/runtime-access/pom.xml:60-101` 实际是 Maven `<profiles><profile id="jdbc-drivers">` opt-in profile，非 generic "conditional"；`ojdbc8` 23.2.0.0 与任务卡 23.3.0.23.09 要求**版本号不匹配**（任务卡 scope 是 23.3.0.23.09；actual 23.2.0.0 是从 .m2 复用 T7 遗留）；`kingbase8` 8.6.0 ✅；`opengauss-jdbc` 5.1.0-og、`DmJdbcDriver17` 8.1.3.62、`mssql-jdbc` 12.6.1.jre11 | pom.xml:64–98 | ⚠️ P1-04 profile 激活陷阱：项目无 `-Pjdbc-drivers` 默认激活，`mvn -pl data-engine/data-engine-impl -am install -DskipTests` **无法拉到 5 个 JDBC 驱动**；Fullstack 验收时绕开了此陷阱（详见 P0-02） |
| 3 | `JdbcConnectionTester` 类 | 新建 `connectionTest(DataSourceConfigVO)`，`Class.forName` catch CNFE | **类不存在**；JdbcConnector.java 仅暴露 `testConnectionDetailed(String, String)`，签名不匹配 | `search_files JdbcConnectionTester` total_count=0；`JdbcConnector.java:58` | ❌ **P0-01** |
| 4 | Controller 解密 | `POST /test-connection/{id}` 调 `sensitiveDecryptionService.decrypt(connectionConfig, "password")` | PMO45DataSourceController.java:103–122 直接 `dsImpl.testConnectionById(decodedId)`，**无 decrypt 调用**；`grep -rn "decrypt\|Encrypt" engine/data-engine` 0 命中 | PMO45DataSourceController.java:103–127 | ❌ **P0-02** 或 P1-01（取决 Fullstack 是否认为"明文"是有意降级） |
| 5 | `SUPPORTED_TYPES` 含 5 种新增 | ORACLE/MSSQL/DM/KINGBASE/GAUSS | `DataSourceServiceImpl.java:40–44` | ✅ verified |
| 6 | `MINIO_TYPE` 常量 | "MINIO" | `DataSourceServiceImpl.java:46` | ✅ verified |
| 7 | `validateType` 新增 MINIO 版本检查 | profile 非 enterprise/ultimate 时抛 400 | `DataSourceServiceImpl.java:278–297` `throw new IllegalArgumentException` | ⚠️ P2 — 异常类型是 `IllegalArgumentException`，不会经 GlobalExceptionHandler 自动转 400；需确认 catch 链 |
| 8 | `testConnectionById` | 读 DB config → 调 JdbcConnector | `DataSourceServiceImpl.java:314` 起（行号未读全） | ⚠️ 需读 314+ 段确认；已确认存在 |
| 9 | `previewSchema` | 关系型调 listResources + queryPreview；MINIO/FS 占位 | `DataSourceServiceImpl.java:494–551` | ⚠️ **P1-02** MINIO/FS 占位 = 直接 return 空列表 + note "待后续任务实现"，**不做** MinioClient 验证（任务卡 scope 明文要求 MINIO: `MinioClient` effective only for enterprise/ultimate） |
| 10 | ConnectorFactory 白名单 | ORACLE/MSSQL/DM/KINGBASE/GAUSS → JDBC | `ConnectorFactory.java:65` 仅 `MINIO","MINIO"`，**未见 ORACLE/MSSQL/DM/KINGBASE/GAUSS 别名**，但 `JdbcConnector.supportedType()` 归一？需确认 `supportedType()` 实现 | ⚠️ 需读 `JdbcConnector.java:30–56` 确认 `supportedType()` 是否含这 5 种 |
| 11 | `VersionPrefixRewriteFilter` 双路径 | `api/v1/datasource/**` 映射 | `VersionPrefixRewriteFilter.java:50–51, 73–74` | ✅ verified |
| 12 | `SecurityConfig` permitAll | `/api/v1/datasource/**` | `SecurityConfig.java:107` | ✅ verified |
| 13 | `ClearanceInterceptor` 豁免 | `/api/v1/datasource` | `ClearanceInterceptor.java:141` | ✅ verified |
| 14 | `POST /api/v1/datasource` (ORACLE) → 201 | curl 凭证通过 | 未验证（前端不联后端时不可测） | ⏸️ 未验证 |
| 15 | `POST /api/v1/datasource` (MINIO, standard) → 400 | 同上 | 未验证 | ⏸️ 未验证 |
| 16 | `POST /api/v1/datasource/test-connection/{id}` → 200 with `{success:false, error:"...oraracle..."}` | 同上 | 未验证 | ⏸️ 未验证 |

## 3. 缺陷（含 severity / evidence / scope / cost）

### P0-01 — `JdbcConnectionTester` 类未实现

- **位置**: 任务卡 scope 中 `data-engine/impl/.../connector/JdbcConnectionTester`（Sync phase 原路径建议 `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/connector/JdbcConnectionTester.java`）
- **证据**: `search_files` 找不到任何 `JdbcConnectionTester` 类文件；`JdbcConnector.java` 第 35 行已有 `testConnectionDetailed(String connectionConfig, String dsType)`，签名与任务卡的 `connectionTest(DataSourceConfigVO)` 不匹配
- **影响**: 任务卡交付物"9 新增 `JdbcConnectionTester` 类"**完全缺失**；`previewSchema` 实际依赖 `JdbcConnector.testConnectionDetailed`，但 `testConnectionDetailed` 仅工作中支持 POSTGRESQL/MYSQL 两条 url 拼装（行 118–161），ORACLE 未实现 `buildOracleUrl()` 也不是有 serviceName 支持的 URL，与 P0-02 互锁。
- **修复建议**: 二选一：(a) 按任务卡新建 `JdbcConnectionTester` 类 + 走 `DataSourceConfigVO`（与 `JdbcConnector.testConnectionDetailed` 类比，新增 5 种 `build<Type>Url` 分支）；(b) 把 `JdbcConnector.testConnectionDetailed` 拆出 `buildOracleUrl/buildMssqlUrl/buildDmUrl/buildKingbaseUrl/buildGaussUrl`，再把 Controller `testConnectionById` 改入参 VO 而非 String。推荐 (b) 因为不增加类，且与现有 `JdbcConnector` 一致。
- **cost**: M（0.5h） — 4 个 build*Url 分支 + 改 Controller 调用签名

### P0-02 — 密码明文存储/传递，无 Controller 层 decrypt

- **位置**: `DataSourceServiceImpl.java:150–194`（update 路线）与 `:314+`（testConnectionById 路线）；`PMO45DataSourceController.java:103–122` 无 decrypt 调用
- **证据**: 任务卡明文"password: plaintext read from config (decrypted at Controller layer via `security-engine`)"；`grep -rn "decrypt\|Encrypt" engine/data-engine` 0 命中；密码出现在 `connectionConfig` JSON 内**无任何加密/脱敏**处理；`JdbcConnector.java:61` 直接 `JSON.parseObject`，password 与 url/host/port 一起透传。
- **影响**: 明文密码会出现在 (a) DB `datasource_connection_config` 列；(b) Postgres 异常栈（`log.warn "JdbcConnector.testConnectionDetailed FAILED type={} url={}"` 第 108 行只打 url 不打 password，但 DB 异常 message 通常含 connection properties）；(c) ERROR 日志 JSON 中 `connectionConfig` 全量字段（若 ServiceRouter 或 GlobalExceptionHandler 带 body 序列化）。违反架构铁律 "security 一律走 security-engine，禁止引擎内重复实现；security 不可用时默认 DENY"。
- **修复建议**: 与 Fullstack 对齐 security-engine 是否提供 `password` 类型密钥（如 `SensitiveFieldDecryptor` / `SecurityEncryptService.decrypt(String, SensitiveField.PASSWORD)`）；(a) 创建/更新 `PMO45DataSourceController` 调 `secureManager` 解密 password 后只把明文 password 注入 `connectionConfig`，**不再持久化**到 `datasource.connection_config`（拆成 `datasource.password_encrypted` + `datasource.password_salt` 两新列，DDL 走 security-engine 标准组件）；(b) `testConnectionById` 读取时解密。走 (a) 才能根治。
- **cost**: XL（>1h） — 涉及 DDL 改动，需走 security-engine 标准组件 + 双路径 Action

### P1-01 — `previewSchema` MINIO/FILESYSTEM 路径"占位"，无 MinioClient 真实验证

- **位置**: `DataSourceServiceImpl.java:511–516`（`if (MINIO_TYPE.equals(type) || "FILESYSTEM".equals(type))` 直接 return 空 objects/columnPreview）
- **证据**: 任务卡 scope 明文要求 "MinIO 验证: `MinioClient` effective only for enterprise/ultimate profiles"；actual 仅做 profile 检查后**占位返回**，没有真正调用 `MinioClient.listBuckets` 验证连通性。
- **影响**: `preview-schema` 端点对 MINIO 数据源**失去对真实性**的基础作用——用户配置错误时 preview-schema 永远成功返回空列表，与任务卡 acceptance 的 "MINIO standard→400" 同样在测试路径下不可达，但实际连通性检查被跳过，与用户预期"预览就是验证"相悖。
- **修复建议**: `previewSchema` 内对 MINIO 走 `MinioClient.builder().endpoint(...).credentials(...).build().listBuckets()`（`runtime-access` 已有 `MinioStorage` 可复用），捕获 `InsufficientDataException/InsufficientCapacityException/ErrorResponseException` 返回 `{success:false, error: ...}`；FILESYSTEM 走 `Files.exists(basePath) && Files.isReadable(basePath)`。
- **cost**: M（0.5h）

### P1-02 — Controller 解密路径缺失导致"plaintext 退化"被绕过

- **位置**: 与 P0-02 同根因，但在 `previewSchema` 层放大
- **证据**: `DataSourceServiceImpl.java:526` `String cfg = ds.getConnectionConfig();` 后直接调 `connector.listResources(cfg, null, null)`，password 明文经 JSON 字符串在 (a) response 体 `columnPreview`（含 `row[password_column_name]` 的列名/值）→ 可能被审计日志捕获 → P0 升级风险；(b) 异常路径 `log.warn "previewSchema failed for datasource={}: {}"` 也不打 cfg 原文，OK
- **影响**: `preview-schema` 旁路独立验证：(1) 无密钥边界，(2) 列名/列值可能泄露敏感字段
- **修复建议**: 与 P0-02 同路线收口；短期：在中国版测试环境用 `application.yml` 加 `connector.debug.details=false` 限制 `JdbcConnector.testConnectionDetailed` 第 108 行的 warn 不输出 url/password 明文
- **cost**: M（0.5h，可与 P0-02 合并）

### P1-03 — `VersionPrefixRewriteFilter` 反向重写表 `/datasource/` → `/api/v1/datasource/` 可能影响 Olap/OLAP 路由冲突

- **位置**: `VersionPrefixRewriteFilter.java:74`
- **证据**: `Map.entry("/datasource/", "/api/v1/datasource/")` 是**单向前缀**——若其他 Controller 用 `@RequestMapping("/datasource/api/...")` 形式（如未来新增数据源规则 `POST /datasource/rule`），会被静默重写为 `/api/v1/datasource/rule`，与 PMO45 `POST /api/v1/datasource/rule` 同 path 冲突（同 Controller 不会互踩，但其他引擎采用此 prefix 时会）
- **影响**: L4——前缀重写源不收敛，跨引擎路由冲突低概率但现实
- **修复建议**: 给 `VersionPrefixRewriteFilter` 加 `Paths` 白名单，模板化（`/api/v1/<engine>/<version>/...`），不在裸 `/` 层留兜底
- **cost**: L（0.25h）

### P1-04 — `runtime-access/pom.xml` JDBC 驱动放在 `<profile id="jdbc-drivers">` 而非常驻 deps

- **位置**: `runtime/runtime-access/pom.xml:60–101`
- **证据**: 默认 `mvn -pl data-engine/data-engine-impl -am install -DskipTests` 不激活 profile，`.m2` 中 `mssql-jdbc/DmJdbcDriver17/DmJdbcDriver17/DmJdbcDriver17/DmJdbcDriver17` 等 4 项均**不存在**（`search_files` 已确认 `mssql-jdbc/DmJdbcDriver/kingbase8/opengauss` 在 .m2 中 0 命中）；`DmJdbcDriver17` 与任务卡 scope "DmJdbcDriver16 8.1.3.62 (no 17)" **artifactId 不匹配**（任务卡要 16，实际 17）；`opengauss-jdbc` 5.1.0-og 任务卡要求 5.1.0-2
- **影响**: 默认构建**不含** 5 种 JDBC 驱动，验收路径"建 ORACLE 数据源 201 + test-connection ORACLE 返回 `{success:false, error:"驱动未加载"}`"**只可用**是因为 JdbcConnector 第 76 行 `Class.forName(driver) catches ClassNotFoundException` 返回友好错误——**但这恰是任务卡要求的行为**，验证一致性 ✅。然开发环境要在 enterprise 档真连 ORACLE，需 `mvn -Pjdbc-drivers`，无说明文档
- **修复建议**: (a) 文档化（`runtime-access/README.md` 加 "可选 profile jdbc-drivers"）；(b) 或把 `kingbase8/opengauss` 提升到常驻 `<optional>true</optional>` deps，避免运维误判
- **cost**: S（0.25h）

### P2-01 — `validateType` 抛 `IllegalArgumentException` 而非 `ValidationException`

- **位置**: `DataSourceServiceImpl.java:283–296`
- **证据**: 任务卡 scope 要求 `ValidationException("仅 enterprise/ultimate 版本支持 MinIO")`；actual 抛 `IllegalArgumentException`（line 283）+ 自定义 message（line 286–296）；`GlobalExceptionHandler` 对 `IllegalArgumentException` 是否转 400 需确认
- **修复建议**: 改抛 `org.springframework.security.access.AccessDeniedException` 或项目内 `ValidationException extends RuntimeException`（与已有 400 处理一致）；最小路径：加 `@ResponseStatus(HttpStatus.BAD_REQUEST)` 注解到 `IllegalArgumentException`
- **cost**: S（0.2h）

### P2-02 — `validateType` 中 MINIO profile check 用 `profile.contains("enterprise"|"ultimate")` 而非精确匹配

- **位置**: `DataSourceServiceImpl.java:291-295`
- **证据**: `profile.contains("enterprise") || profile.contains("ultimate")` — 在 Spring 多 profile 场景（如 `enterprise,prod`）下 OK，但 profile 名意外拼写（如 `enterprise2`）也会匹配；安全口径建议精确匹配
- **修复建议**: 改用 Spring `spring.profiles.active` 集合判断，或加正则边界匹配
- **cost**: S（0.1h）

## 4. 门禁判定

```
{"isPartial": true,
 "review_succeeded": false,
 "artifacts_verified": [`data-engine-impl-1.0.0-SNAPSHOT.jar` build-ok 13:11:38, `runtime-access-1.0.0-SNAPSHOT.jar` 13:11:06],
 "findings": [
   {"id":"P0-01","severity":"P0","category":"missing-deliverable","evidence":"search_files JdbcConnectionTester total_count=0","scope":"JdbcConnector.java / DataSourceServiceImpl.java","fix_cost":"M (0.5h)"},
   {"id":"P0-02","severity":"P0","category":"security","evidence":"grep -rn decrypt engine/data-engine 0 hits; PMO45DataSourceController 无 decrypt 调用","scope":"PMO45DataSourceController.java:103-122, DataSourceServiceImpl.java:314+","fix_cost":"XL (>1h) 需 security-engine 协作 + DDL"},
   {"id":"P1-01","severity":"P1","category":"logic","evidence":"DataSourceServiceImpl.java:511-516 MINIO/FS 占位 return 空","scope":"DataSourceServiceImpl.java","fix_cost":"M (0.5h)"},
   {"id":"P1-02","severity":"P1","category":"security","evidence":"previewSchema 第 526 行 String cfg = ds.getConnectionConfig() 明文经 connector.listResources 透传","scope":"DataSourceServiceImpl.java","fix_cost":"M (0.5h) 可合并 P0-02"},
   {"id":"P1-03","severity":"P1","category":"architecture","evidence":"VersionPrefixRewriteFilter.java:74 单向前缀 /datasource/→/api/v1/datasource/ 无白名单","scope":"VersionPrefixRewriteFilter.java","fix_cost":"L (0.25h)"},
   {"id":"P1-04","severity":"P1","category":"build","evidence":"runtime-access/pom.xml:60-101 jdbc-drivers profile 默认不激活; search_files .m2 中 mssql-jdbc/DmJdbcDriver/kingbase8/opengauss 均 0","scope":"runtime-access/pom.xml","fix_cost":"S (0.25h) 文档化或提常驻"},
   {"id":"P2-01","severity":"P2","category":"api","evidence":"DataSourceServiceImpl.java:283-296 抛 IllegalArgumentException 而非 ValidationException","scope":"DataSourceServiceImpl.java","fix_cost":"S (0.2h)"},
   {"id":"P2-02","severity":"P2","category":"logic","evidence":"DataSourceServiceImpl.java:291-295 profile.contains('enterprise'|'ultimate') 用 contains 而非精确匹配","scope":"DataSourceServiceImpl.java","fix_cost":"S (0.1h)"}
 ],
 "deliverable_allowed": false,
 "unverifiable": [["curl POST /api/v1/datasource (ORACLE) → 201", "requires running gateway + env"], ["curl POST /api/v1/datasource (MINIO in standard) → 400", "requires running gateway + env"], ["curl POST /api/v1/datasource/test-connection/{id} → 200 {success:false,...}", "requires running gateway + env"]],
 "notes": "实际交付路径相对任务卡的偏差: `JdbcConnectionTester` → 直接用 `JdbcConnector.testConnectionDetailed`（简化但不出新类）; 无 `DataSourceBaseVO/DataSourceConfigVO`（实际 `DataSourceEntity` + `ConnectionConfig` POJO）; security-engine 真正撞墙后退回明文存储（见 P0-02）。**P0-02 是 security 决策点，需 Fullstack 与 Arch 共同决定**：要么改 DDL 走 security-engine 标准组件，要么在 README 中声明该模块不在 security 范围内（违反架构铁律，不建议）."}
```

## 5. 下一动作

- **Fullstack**：按 P0-01 → P0-02 → P1-01/02/03/04 → P2-01/02 顺序修复；P0-02 先与 Arch 对齐 security-engine 是否提供 `dataSource.password` 标准组件 + DDL；建议补 `runtime-access/README.md` 说明 `jdbc-drivers` profile
- **Arch**：rm_arch §14 `security-engine password 标准组件` 接口决议建议（如 `SecurityEncryptService.decrypt(String ciphertext, SensitiveField) → String`）+ DDL schema 命名建议
- **PM**：产物呈现 = 飞书群同步 + 不落本地 review 目录
- 总 cost 估算：1.5–2.0 h
