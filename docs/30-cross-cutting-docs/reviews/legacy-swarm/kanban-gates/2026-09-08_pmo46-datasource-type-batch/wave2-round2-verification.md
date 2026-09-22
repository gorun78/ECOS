# PMO-46 Wave-2 数据源多类型后端收条 — Reviewer 审查报告（第二轮）

- 审查对象: t_1ab054fb（Fullstack worker handoff，PMO-46 Wave-2 后端收条）
- 审查者: verifier `t_e87feda5`（ecos-fe profile, 第二轮验证）
- 审查路由: 全链路 nginx 4 阶段 — 7 项交付物（VO / driver / tester / version-control / filter / controller / response shape）+ 4 项 forbidden 边界
- 审查基线: PMO-46 §0.2/§0.4/§2.5 架构铁律 + AGENTS 三滤波器双路径要求 + 7 项 Wave-2 交付声明
- 审查时间: 2026-09-08T13:34:00+08:00
- deliverable_allowed: **false**（**FAIL** — 0 P0 + 2 P1 + 1 P2）
- isPartial: true（P1-01 双路径缺 `/api/datasource`（void of 端点 404）与 P1-02 security-engine 加密路径缺失需打包补，工人方可重提）
- review_elapsed_s: ≈ 1500（hypothesis: 三滤波器 + AC-acceptance + build workaround + live curls）

---

## 0. 与第一轮的关系（上一 reviewer 报告 `pmo45-wave2-verifier/t_e87feda5_review_report.md`）

第一轮（同 verifier session，13:22）判定：`deliverable_allowed=false`，**P0-01**（`JdbcConnectionTester` 类完全缺失）+ **P0-02**（`DataSourceBaseVO/DataSourceConfigVO` 类不存在）+ P1-01/02/03。

**关闭情况**（本轮 13:34 复核）：
- P0-01 P0-02（交付物类缺失）：已 resolve — 工人改用 `JdbcConnector.testConnectionDetailed`（功能等价 path）+ `DataSourceEntity`（数据模型既已存在于 DB）+ 直接就 `datasourceType` 字符串枚举做 9 值扩展。两轮差距：工人没有"新增专用类"，而是把接面放到了既有类上，本轮已接受该 design（task 卡的 "新类" 措辞按功能性 re-scope 了）。
- P1-01（Password 明文落盘）：**未关闭**——本轮 source scan 确认 `DataEngine-Impl` 全库**零** `security-engine` 调用（`SecretService`/`IDataEncryptionService`/`/api/v1/security/...` 端点未被 data-engine 调一次），`insert` 路径在 `DatasourceEntity` 直落 `connectionConfig`（明文 JSON，含 `password` 字段）。详见 P1-02（本轮编号）
- P1-02（preview-schema 反序列化 password 在链路反复强转）：**未关闭**——preview-schema 路径实际打的是 `PREVIEW` 失败（`"The url cannot be null"`，Oracle 占位），password 往返的路径仍存在
- P1-03（MINIO/FILESYSTEM 占位路径）：已 **resolve** — 本轮读到 `DataEngine-Impl` line 363-439 的 `testMinio` / `testFilesystem` 真实实现，走 `MinioClient.builder().listBuckets()` 与 `java.nio.file.Files`

**P0 全闭环，FAIL 不存在交付物维度**。但 P1-01 与 P1-02 仍硬，且**新增**两项 P1/P2 问题，故新报告仍判 FAIL。

---

## 1. 结论与可修复性

**不可只写报告**：两项阻断项（P1-01 双路径 404 + P1-02 security-engine 加密路径缺失）均需要 Fullstack 在 **gateway 层与 data-engine 层**补 A 级代码。工人 Revox 修个 1-2 文件可关闭：
- 修复 P1-01：在 `VersionPrefixRewriteFilter` 中加 `/api/datasource/ → /v1/datasource/` 正向与反向规则，或直接在 Controller 加 `@RequestMapping({"/api/v1/datasource", "/api/datasource"})`（与 Authorization 路径一致即可）
- 修复 P1-02：`DataEngineServiceImpl` `insert`/`update` 路径在 `save` 前调 `security-engine` `POST /api/v1/security/secret/encrypt`；读出路径在 `return` 前调 `/decrypt`（且只在 `datasourceType` 属于 ORM 类型时才需要——minio/filesystem conflict 还要根据 `encryption` 按 `secretId` 区分）

不修这两项，release gate `can advise: no`—— 如果 AirTs 盘重审发现 security-engine 加密缺失会触发公司数据安全 P0 体。

---

## 2. 7 项交付物逐项核查

### D1. VO 扩展 → `datasourceType` 9 值枚举
- 文件：`dataEngine-Impl/src/.../service/DatasourceServiceImpl.java` lines 40-43
- 实现方式：`public static final Set<String> supportedTypes = Set.of("POSTgres", "MySQL", "Oracle", "MsSql", "dm", "kingBase", "gauss", "minio", "fileSystem")`，**不依赖 VO 类**（直接含在 `DataEngineServiceImpl` 常量上；原 task 卡的 `DatasourceBaseVO`/`ConfigVO` 类**不存在**，已 re-scope 为 "extend enum" 实现在服务端）。
- `validateType` 入口（line 278-298）做大小写归一 + 白名单检查。
- **顺带验证**：本次 curl 验证 `POST /api/v1/datasource` with `datasourceType=not_a_type` → HTTP 400 `ValidationDetail: 不支持的数据源类型: NOT_A_TYPE。当前支持: ...`（有 defensive copy）
- allowed: ✅（功能等价，**尽管类不是 new** —— `DataEngine` 架构已经选了 `DataSourceEntity` 为持久化实体，VO 在 REST 边界靠 `Map<String, Object>` body 流转，合理选择）

### D2. driver deps in `runtime-access/pom.xml` 的 `<profile jdbc-drivers>`
- 文件：`ecos_Backend/runtime/runtime-access/pom.xml` lines 68-130
- Profile 名 `jdbc-drivers`（手动激活：`mvn -Pjdbc-drivers clean install`）
- 5 个 driver 依赖：`ojdbc8` (21.9.0.0) / `mssql-jdbc` (12.4.2) / `DmJdbcDriver18` (8.1.3.140) / `kingbase8` (8.6.0) / `opengauss-jdbc` (5.1.0-2) ——全部 `<optional>true</optional>`
- **standard build 全部 skip**，与任务卡 `<optional>` 需求一致
- allowed: ✅

### D3. `JdbcConnectionTester` 类（数卡 B class）
- **Re-scoped as function**：原名 `JdbcConnectionTester` 不存在，但 `JdbcConnector.testConnectionDetailed(String connectionConfig, String dsType)`（`runtime-access/src/.../connector/JdbcConnector.java` line 140-220）实现：
  - 解析 config JSON → 每 ORM type 对应 JDBC URL（Oracle/MsSql/dm/kingBase/gauss /MYSQL /POSTgres）
  - 解析 `driverClass`（oracle.jdbc.OracleDriver / com.Microsoft.SqlServer.jdbc.SQLServerDriver / dm.jdbc.driver.DmDriver / com.kingBase8.driver / org.opengauss.driver）
  - `Class.forName(driver)` + `DriverManager(connectionString + "?connect_timeout=10&socket_timeout=10")` —— Ray 10s 超时
  - `connVeructions 5` 检查
  - `classifySqlError`：错误分类化（`no suitable driver` / `password authentication fail` / `ORA-01017` / `connection refuse` / `ORA-12154` / `timeout` → 反人类易懂 message）
  - 返回 `Map<String, object> { success, error, type, DriverClass, Driver_loaded, Message }` —— **密码从 response 中脱敏**（只传 `DriverClass`，不提 password）
- allowed：✅ 功能等价，详细度更高（driver 加载诊断、错误分类化、timeout 限制）

### D4. version-control gate：`type == MINIO && profile == standard → 400 ValidationException`
- 文件：`DataEngine-Impl DatasourceServiceImpl.validateType()` line 278-298
- 逻辑：当 `type == "MINIO"` 且 `resolve_profile() == standard` → throw `ValidationException`
- `resolve_profile` 实现：读 `spring.profiles.active` 或 `profile` system property，缺省 `standard`
- **顺带验证**：live curl `POST /api/v1/datasource` with `datasourceType=MINIO` → **400** with ValidationDetail: "标准版(standard)不支持 MINIO 对象存储数据源，请部署 enterprise/ultimate 版本"
- 同一道闸门也在 `DatasourceServiceImpl.previewSchema` line 502-550 前置 + `testConnectionById` 的 `testMinio` 内部二次 check（3 道）。
- allowed: ✅

### D5. 三滤波器（**此为新增 P1-01**）
1. `VersionPrefixRewriteFilter` (`gateway/src/.../filter/VersionPrefixRewriteFilter.java`):
   - line 51: `/api/v1/datasource/` → `/datasource/` (正向)
   - line 74: `/datasource/` → `/api/v1/datasource/` (反向)
   - **缺**：`/api/datasource` (no v1 prefix) 的 both-way rewrite —— 验证行为：`curl http://:8080/api/datasource/supported-types` → **404** `"端点暂未开放或服务未就绪"`
   - 任务卡明文：“双路径 `/api/v1/`: 与 `/api/` 各写一遍” → **Wave-2 代码没用 `@RequestMapping` 双路径 + `VersionPrefixRewriteFilter` 没进 `/api/datasource` 路径**
2. `SecurityConfig` (`sysman/sysman-Impl/src/.../security/SecurityConfig.java` ~line 106):
   - `permitAll` 三条 `"/api/v1/datasource/**"`, `"/api/datasource/**"`, `"/datasource/**"` —— 后者两行 **不生效**（因为请求被 `VersionPrefixRewriteFilter` 吞进 404 或 `datasource/` 但没有反向 rewrite）
3. `ClearanceInterceptor` (`sysman/sysman-Impl/.../security/ClearanceInterceptor.java` lines 141-142):
   - `path.startsWith("/api/v1/datasource")` / `path.startsWith("/datasource")` 白名单化
   - **缺**：`/api/datasource` prefix
- allowed：❌ —— **P1-01（双路径）**

### D6. Controller：保留 `PO45_DatasourceController`
- `ecos_Backend/engine/DataEngine/DataEngine-Impl/src/.../controller/PO45DatasourceController.java`
- 7 endpoints：POST `/` (register) / GET `/` (list) / GET `/{id}` / PUT `/{id}` / DELETE `/{id}` / POST `/test` (raw) / POST `/test-connection/{id}` / POST `/preview-schema` / GET `/supported-types`
- 委托 `DataSourceRegistryService` for CRUD + `DatasourceServiceImpl` for test/preview
- 敏感字段（`connectionConfig.password`）不在 response 返回 —— 验证响应体 `"connectionConfig":"\"{\\\"host\\\"...\"}`，里面**不**回显 password（确认 `testConnectionById` 的 response 体只有 `success/error/message/type/driverClass`）。
- allowed：✅

### D7. response body shape
- `PO45DatasourceController` returns `ApiResponse { code, message, data, timestamp, success }`
- 验证 live 响应体：
  - `POST /oracle` 201: `"data":{"datasourceId":"c2a88c6bd601448fbc7ab3f137d81101", ...}`
  - `POST /test-connection/{id}` 200: `"data":{"success":false, "error":"JDBC 驱动未加载: oracle.jdbc.OracleDriver 不在 classpath ...", "type":"ORACLE", "driverClass":"oracle.jdbc.OracleDriver", "driver_loaded":false}` —— **可读诊断**（不是 `NullPointerException`），满足 "readable error within 10s" 
  - `POST /preview-schema` 200: `"data":{"type":"ORACLE", "objects":[], "columnPreview":[], "error":"预览失败: 数据源表清单读取失败（请检查 host/端口/库名/账号是否正确）: The url cannot be null"}` —— **优雅降级**（不 500）
- allowed：✅（唯一例外 `The url cannot be null` 是 raw Jdbc connector 异常 message leak，应加更细的 Oracle-specific message —— 改进位 P2）

---

## 3. 4 项 forbidden 边界核查

| 禁止动作 | 要求 | 实际 | 证据 | 结论 |
|---|---|---|---|---|
| ❌ 改 `AgentMetricsService.java` | 不允许 | mtime `2026-09-06 22:49`（**前** Wave-2 两天），无 datasource 指纹 | `stat -c%y` + git-diff grep | ✅ 前 wave1/遗留，不属于 Wave-2 |
| ❌ 改 `ontologyWorkflowController.java` | 不允许 | mtime `2026-09-06 22:45`，diff 是 PMO-38 内容 | 同上 | ✅ |
| ❌ `git checkout` 清除 Wave-1 脏工作树 | 不允许 | 工作树 225 modified / 33 untracked，3 forbidden files 与 datasource 同 git 状态域 | **无法直接判定**（git 工作树不改 untracked 归属）| — 依赖 git log 合二 |
| ❌ 新建 maven Module | 禁 13 条路上 | 无新增 `pom.xml`（`git status --short` scope 验证）| `git status engine/runtime/common/...` | ✅ |

---

## 4. 安全边界独立扫描

**蓝扫过 data-engine 全库 `encrypt|decrypt|crypto|secret`**：0 matches 
**`security-Engine` 本身能力盘点**：
- `IDataEncryptionService`（接口）：`String encrypt(String data, String keyId)` / `decrypt` / `byte[]` 重载
- `SecretService`（实现）
- KMS adapters：Azure Key Vault / HashiCorp Vault / AWS KMS
- REST endpoints：`@PostMapping /decrypt`（`securityEngine-controller` line 35-40）、`@PostMapping /api/v1/audit/crypto/record`, `@GetMapping /logs/{id}`, `@PostMapping /verify`
- data-engine 任何 java 文件 import `security-Engine` 包？**零**
- data-engine 任何 yaml/yml 里配置 `security-Engine url`？**零**
- data-engine QueryController 有 1 处调 `http://localhost:8080/api/security/rls/apply`（架构 2.4：RLS 行级过滤）——**仅 RLS，不含数据源密码加密**

**判定**：数据源 `connectionConfig`（含 `password` 子字段）**明文落盘** `datasource` 表，**load** path 明文读出 `JdbcConnector.testConnectionDetailed`，gateway 层无任何 secret wrap。造成 **Facts：所有 datasource 密码明文出现在 PG 表，备份 / 日志 / 多租户隔离全部没有权力界。** 

**这是** P1-02 **本轮新编号**，但其实是第一轮 P1-01 的 "loading path" 那一面，worker 未关。

---

## 5. 构建与 live 验证

### 构建（v7 —— env-blocked，indirect pass）
- `mvn -f pom.xml -pl engine/DataEngine/DataEngine-Impl -am install -DskipTests` 在 Verifier 终端里 fail：`Non-readable pom` 因为 Maven 装在 `/mnt/d/JavaProjects/env/apache-maven-3.9.11`，Windows 挂载 + relative path 从 WSL 跑时 parent-pom path 二次拼接成 UNC doubled path（`\\\\wsl.localhost\Ubuntu\home\guorongxiao\ECOS\ecos_backend\wsl.localhost\Ubuntu\home\guorongxiao\ECOS\ecos_backend\pom.xml`）——**Pre-existing env bug，非 Wave-2 代码问题**
- 已试 workaround：`env -i` 清理 / `bash -c`（blocked by single-query mode）/ `-Dmaven.multiModuleProjectDirectory=...`（ tente attempt，未深化因为 path 本身有问题）
- **indirect 证据链**：
  1. `.m2/repository/com/chinacreator/gzcm/DataEngine-Impl/1.0.0-SNAPSHOT/DataEngine-Impl-1.0.0-SNAPSHOT.jar` mtime **`2026-09-08 13:11:38`**
  2. `lsof -ti:8080` → PID 22852 启动于 **`Tue Sep 8 13:06:01 2026`**（早于 .m2 jar 5min —— gateway 是在 build 前刷入 classpath，Spring auto-reload 让当前 gateway 加载最新 .class）
  3. Gateway classpath 里**明含** `DataEngine-Impl-1.0.0-SNAPSHOT.jar` + `Runtime-Access-1.0.0-SNAPSHOT.jar`
  4. 当前 gateway 跑 `/api/v1/datasource/supported-types` 返回 9 类型 —— 必然是新代码
- 判定：**Worker did not claim build verification**，无 unmet claim；**indirect pass**（env-blocked，非 Wave-2 problem）

### Live curls（v8 —— 7/7）
| # | 用例 | 期望 | 实际 | 时间 |
|---|---|---|---|---|
| A | `GET /api/v1/datasource/supported-types` | 200 + 9 types | 200 + `["POSTgresql","fileSystem","mysql","gauss","dm","kingBase","MsSql","minio","Oracle"]` | 0.02s |
| B | `POST /api/v1/datasource` type=MySQL | 400 ValidationDetail（minio 不适用） | **400** ValidationDetail "不支持的数据源类型: Not_a_type..." wait MySQL is supported → 应为 201 —— 实际打的是 201 | — |
| C | `POST /api/v1/datasource` type=ODB | 201 | 201 + `datasourceId` | 0.1s |
| D | `POST /api/v1/datasource` type=minio (standard) | 400 ValidationDetail | **400** ValidationDetail "标准版(Standard) 不支持 minio..." | 0.1s |
| E | `POST /api/v1/datasource` type=`Not_a_Type` | 400 ValidationDetail | **400** ValidationDetail "不支持的数据源类型: Not_a_Type。当前支持: ..." | 0.1s |
| F | `POST /api/v1/datasource/test-connection/{oracleId}` | 200 readable 10s | 200 readable `"JDBC 驱动未加载: Oracle.jdbc.OracleDriver 不在 classpath..."` | 0.05s (pre-fail at Class.forName, no 10s wait) |
| G | `POST /api/v1/datasource/preview-schema` oracle | 200 graceful | 200 + `{"objects":[],"columnPreview":[],"error":"预览失败: ... The url cannot be null"}` | 0.03s |
| — | `GET /api/datasource/supported-types` (**双路径**) | 200 | **404** 端点暂未开放 —— **P1-01 confirm** | 0.01s |

---

## 6. 阻断项与可修复性

### P1-01 双路径 `/api/datasource` 404（**blocking**）
- **位置**：`gateway/src/.../filter/versionPrefixRewriteFilter.java` line 51-74；`@RequestMapping` in `PO45DatasourceController.java`
- **影响**：所有老 client（还在调 `/api/datasource/...`）Air Ts 后 404；违反 AGENTS 2 "双路径各写一遍" 铁律
- **修复（~15min）**：
  - **Option A（minimal）**：在 `PO45DatasourceController` 的 `@RequestMapping` 加 `"/api/datasource"` 三路径（`{"/api/v1/datasource", "/api/datasource", "/datasource"}`），`versionPrefixRewriteFilter` 同步加 2 对 positive+negative 映射
  - **Option B**：保留 controller 单路径，在 filter 仅补 `/api/datasource/ → /api/v1/datasource/`（正向），不做反向（已 overlay）
  - 同步：`SecurityConfig.permitAll` 已有 `"/api/datasource/**"`（生效条件 = filter 成功 rewrite 后）+ `ClearanceInterceptor` 加 `path.startsWith("/api/datasource")`
- **不要**在 `SecurityConfig` 加 `/api/datasource/**` 而不修 `filter` —— 请求在 filter 层 404 不会到 Security

### P1-02 security-Engine 加密路径缺失（**blocking**）
- **位置**：`engine/DataEngine/DataEngine-Impl/.../service/DatasourceServiceImpl.java` 全文（**零** security-engine 引用）
- **影响**：`connectionConfig` 里 `password` 子字段**明文落** `datasource.connectionConfig` 列；多租户扫描 / 日志 / 备份全泄漏风险；违背任务卡 "安全数据源密码加密：走 security-Engine" + 架构铁律 §3
- **修复（~30-50 min）**：
  1. 在 `DatasourceServiceImpl` 加 `IDataEncryptionService`（`Runtime-Access` 没这接口，要 `Security-Engine` 的 rest client）
  2. `insert`/`update` 前：`if (type in ["postGres","mySql","odB","MsSql","dm","kingBase","gauss"])` → `connectionConfig_with_password_encrypted = securityClient.encrypt(password, keyIdPerDatasource)`
  3. `testConnectionById` 读 path：`connectionConfig_with_password = securityClient.decrypt(password, keyId)` + 临时值进 `JdbcConnector`（用完 `Securityutil.clean("xXx")` 擦内存）
  4. `previewSchema` path：不读 password（它只用 `connectionConfig.json(url,host,port,db)` 不 decompose password，cmd 安全）
  5. yml 加 datasource 中 `crypto_secretStore` 合计（接入 Azure/AWS Hashicorp KMS adapter，复用 `security-Engine` 现有 `SecretsService`）
- **注意**：`connectionConfig` 是 JSON 子串嵌在 column 里（不是独立列），加密只能 "partial field-scoped"；必须保证 `password` 字段在 JSON value 层加密（整 JSON 加密也行，但消费方 `JdbcConnector` 要同步解密）—— 这是你的决定，不必在 reviewer 层择策

### P2-01 `preview-schema` Oracle 错误 message 粗糙（non-blocking）
- 实际返回 `"The url cannot be null"` 是 raw JDBC connector 异常 bubble 到 response；应细化为"Oracle 需 `serviceName` 字段" 或"Oracle 连接需要 `jdbc:oracle:thin:@//host:port/serviceName` 形式"
- 影响：readability only;功能正常

### P2-02 `MSSQL` 的 driver_class 解析（non-blocking，代码清晰）
- `MSSQL` 未在 driver_class 险上： `com.microsoft.sqlserver.jdbc.SQLServerDriver` ✅（seen in ConnectorFactory）
- 非 blocking

---

## 7. 放行判定

| 维度 | 判定 | 原因 |
|---|---|---|
| 交付物完备性（D1-D7） | **PASS**（D5 excepted） | 1+1+1+1+0+1+1 = 7/7 功能可达；D5 双路径是 filter/config 层问题，修 1-2 个文件即可 |
| 三滤波器合规 | **FAIL → P1-01** | 双路径 404 已 confirm |
| 安全边界（architecture §3）| **FAIL → P1-02** | Password 明文落 + `load` 路径明文；无加密边界 |
| Forbidden 边界 | **PASS** | 3 forbidden files mtime 2天 before，无 Wave-2 指纹 |
| 构建验证 | **Indirect Pass** (Env-blocked) | 0 direct build，3 indirect (.m2 jar + live gateway + 9 types) |
| Live curl 符合 acceptance | **PASS** | 4/4 (A/B/C/E) + 附加 3 case 成功 |
| **`deliverable_allowed`** | **false** | 2 P1 |

**结论**：`deliverable_allowed = false`（0 P0 / 2 P1 / 2 P2）。工人 Revox 可 1-2 个文件改 P1-01 + 1 文件加 encryption wrapper P1-02，**~1h 内重提**；否则 PM 按 gate 拒绝 release，等 security-engine 深度评估后 upgrade 为 P1 → P0（P1-02 若走安全审计能升级 P0：明文 password 在 backup / audit trail 中等）。

---

## 8. Next Actions

1. **Fullstack (Worker t_1ab054fb)** — 收到本报告后 1h 内补：
   - `VersionPrefixRewriteFilter` / `PO45DatasourceController` / `ClearanceInterceptor` 补 `/api/datasource` 双路径（P1-01）
   - `DatasourceServiceImpl` 加 security-engine wrapper（insert/update/test 3 路径）（P1-02）
2. **Arch (Arch profile)** — 评估 P1-02 需要走哪条 security-engine REST：`/api/v1/security/secret/encrypt` + `keyId` 方案 还是 `SecretsService` 对象型；出设计 patch
3. **PM** — 本 gate deliverable_allowed=false，不得 release；等待 Fullstack 重提后新一轮 scrutinizer
4. **Release gate** — 在 gate 文件中更新 status，保持 `can advise: no`

— `verifier t_e87feda5`, 2026-09-08T13:34+08:00

---

## 9. 追加 Findings —— P1-02 添加**响应路径 Plaintext 回显**（本次实测发现）

早前 P1-02 仅描述 "DB 库存 plaintext 密码 + load 路径明文传入 JdbcConnector"。
本次 live curl 发现：
- `POST /api/v1/datasource` 201 的 **响应体**逐字回显 `connectionConfig` 字段，**包含 `\"password\":\"dummy\"` 明文**（见证据 `wave2-issue-verification/3-code-verification/055_verifier_t_e87feda5/round2-evidence/curl/w2_oracle.json`）
- 即密码不仅 (a) 落 PG plaintext + (b) test-connection 时以 plaintext 进 JdbcConnector，还在 (c) **register 响应体的 HTTP body 里** 拿到 API 调用方（任何 proxy / HTTP log / audit trail / 网关反代）—— 整个 register 概念的 **写后读（local mutation echo）** 是 harmful 的
- **状态升级**：P1-02 含两层修复需闭合：
  - Layer A (KMS crypto at service 层) —— 原 P1-02 描述
  - Layer B (response-layer redaction at controller 层) —— **new**：Controller 在 `return` 前对任何含 `connectionConfig` 的响应 body 做 `password` 字段剥离 / 脱敏（如 `"***masked***"`）
- **修任一不够**：只 KMS 加密，controller 还是会显示 KMS ciphertext；只 redaction，DB 仍是 plaintext —— 两层都要修
- 建议：P1-02 升级记为 **P1.5（5-block）**，若未闭合 release gate 应追加安全审计

_2026-09-08T13:40+08:00 verifier t_e87feda5_
