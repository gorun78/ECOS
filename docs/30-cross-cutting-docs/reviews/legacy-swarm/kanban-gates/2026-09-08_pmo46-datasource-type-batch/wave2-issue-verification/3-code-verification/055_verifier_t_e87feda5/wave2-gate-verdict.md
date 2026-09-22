# PMO-46 Wave-2 Round-2 Reviewer Verdict

- Task: t_1ab054fb (Fullstack worker, datasource multi-type batch — Wave 2/PMO-45)
- Reviewer: t_e87feda5 (ecos-fe profile, second verification round)
- Reviewed: 2026-09-08T13:34+08:00
- Report: ./wave2-round2-verification.md (same dir)

## Verdict

**`deliverable_allowed = false`** — **0 P0 / 2 P1 / 2 P2** (FAIL)
**isPartial = true** — P1-01 + P1-02 are both file-level 1-50min rework; not architecture blockers.

### Round-1 P0/P1 closures (pre-existing report `reviews/pmo45-wave2-verifier/t_e87feda5_review_report.md`)

| # | Round-1 | Round-2 status | Evidence |
|---|---|---|---|
| P0-01 `JdbcConnectionTester` 类缺失 | **CLOSED** | Re-scoped to existing class `JdbcConnector.testConnectionDetailed` (`runtime-access/src/.../connector/JdbcConnector.java:140-220`) — functionally equivalent + richer (driver diagnostics, error classification, 10s timeout) | source read + curl F |
| P0-02 `DataSourceBaseVO/ConfigVO` 不存在 | **CLOSED** | Re-scoped to `DataSourceEntity` + 9-value `SUPPORTED_TYPES` enum in `DataEngineServiceImpl:40-43` + `validateType` (line 278-298) | source read + curl A/E |
| P1-01 password 明文落盘 | **NOT CLOSED** | `DataEngine-Impl` 0 refs to security-engine; insert path stores `connectionConfig` (JSON with `password`) plaintext in PG `datasource` table | full grep `encrypt\|decrypt\|crypto\|secret` in data-engine = 0 hits |
| P1-02 preview-schema password in strong-typed deserialization | **NOT CLOSED** | preview-schema still hits `getConnector(MINIO)` and falls back on `ConnectionConfigType` parse; password roundtrip remains | curl G |
| P1-03 MINIO/FILESYSTEM 占位占位 | **CLOSED** | `testMinio` (line 363-401) uses `MinioClient.builder().listBuckets()`; `testFilesystem` (line 406-439) uses `Files.exists/access`; both real, not empty stubs | source read |

### This-round NEW findings

#### P1-01 — 双路径 `/api/datasource` 404 (**blocking**)
- `VersionPrefixRewriteFilter.java` lines 51-74 only rewrite `/api/v1/datasource/` ↔ `/datasource/`, never `/api/datasource` (no v1 prefix)
- **Live 404 confirmed**: `curl http://localhost:8080/api/datasource/supported-types` → 404 `"端点暂未开放或服务未就绪，请稍后重试或联系管理员"`
- Violates AGENTS.md rule 2: "新增 Controller 必过三滤波器... **双路径 /api/v1/ 与 /api/ 各写一遍**"
- `SecurityConfig.permitAll` already lists `"/api/datasource/**"` (line 106 area) — **pre-positioned but never wired** (filter 404s first)
- `ClearanceInterceptor` lines 141-142 also missing the `/api/datasource` prefix check
- **Fix** (~15 min): Option A — add `"/api/datasource"` to `@RequestMapping` in `PMO45DataSourceController` (+ `/api/v1/datasource`, `/datasource` triple); Option B — add `/api/datasource/ → /api/v1/datasource/` to the rewrite filter. Either way, sync `ClearanceInterceptor` whitelist.
- **Do NOT** only add the `SecurityConfig` permitAll — the filter 404s before Security runs.

#### P1-02 — security-engine 加密路径缺失 (Round-1 P1-01 reconfirmed, **blocking**)
- `DataEngine-Impl` references `securityEngine` / `SecretService` / `IDataEncryptionService` exactly **0** times (full grep)
- `insert` path (in `DataServiceRegistryService.insert` consuming `DataSourceServiceImpl.validateType` → `save`) persists `connectionConfig` (incl. `password` JSON field) **plaintext** to PG `datasource` table
- Load path `testConnectionById` (line 322-357) → `JdbcConnector.testConnectionDetailed` parses **plaintext** `password` and uses it for `DriverManager.getConnection`
- Contrast: `QueryController` (line 40-116) already calls `security-Engine REST` (`/api/security/rls/apply`) for row-level filtering — so a data-engine → security-engine REST pattern is already established; only datasource password encryption is missing
- **Violates**: Task card explicit claim "密码：Controller 层从 DB 读出后明文取用（**加密走 security-engine**）" + 架构铁律 §3 "安全一律走 security-engine"
- **Fix** (~30-50 min): inject `SecretClient` / `IDataEncryptionService`; encrypt `password` field at insert/update (per-datasource `keyId`); decrypt at test + use `SecurityCleanupUtils` to wipe memory; `previewSchema` OK to skip (doesn't read password)
- **Security-risk escalation**: if Arch decides to run with this as-is, raw **plaintext** password ends up in DB + backup + audit trail — **escalates to P0** under security audit

#### P2-01 — preview-schema Oracle 错误 message 粗糙 (non-blocking)
- Live response: `"error":"预览失败: 数据源表清单读取失败（请检查 host/端口/库名/账号是否正确）: The url cannot be null"`
- `The url cannot be null` is raw Java `NullPointer`-ish message bubbling up; should be Oracle-specific `"Oracle requires `serviceName` field or `host:port/serviceName` URL format"`
- Readability only; function is graceful (HTTP 200, no 500)

#### P2-02 — `@RequestMapping` inconsistent with task card "new class VOS" (non-blocking, noting for arch)
- Task card names `JdbcConnectionTester` / `DataSourceBaseVO` / `DataSourceConfigVO` as new classes. Worker delivered **functionally equivalent behavior** via existing classes (`JdbcConnector.testConnectionDetailed`, `DataSourceEntity`, 9-value enum in `DataEngineServiceImpl`). This is a valid architectural choice (no dry new-VO classes because REST boundary already uses `Map<String, Object>` body), but **Arch should reconcile the task-card-vs-implementation delta** to keep PMO-46 spec solvent.

## 7-item deliverable score

| # | 交付项 | 结果 | 备注 |
|---|---|---|---|
| 1 | VO extend 9 types | ✅ | `SUPPORTED_TYPES` in `DataSourceServiceImpl:40-43`; 9 values incl `ORACLE/MSSQL/DM/KINGBASE/GAUSS/MINIO/FILESYSTEM` |
| 2 | Driver deps (optional, jdbc-drivers profile) | ✅ | `runtime-access/pom.xml` profile `jdbc-drivers`; 5 deps all `<optional>true</optional>` (OJDBC8 21.9.0.0 / mssql-jdbc 12.4.2 / DmJdbcDriver18 8.1.3.140 / kingbase8 8.6.0 / opengauss-jdbc 5.1.0-2) |
| 3 | `testConnectionDetailed` (JdbcConnectionTester equiv) | ✅ | `JdbcConnector.java:140-220`; Class.forName → DriverManager → isValid → classifySqlError; returns readable diagnostics |
| 4 | MinIO version control (standard→400) | ✅ | Triple-gated: `validateType` (line 278-298) + entry to `previewSchema` (502-550) + inside `testMinio` (363-401); curl D live 400 |
| 5 | 三滤波器 (双路径) | ❌ **P1-01** | `/api/v1/datasource` ✅ + `/datasource` ✅ + **`/api/datasource` ❌ (404 live)** |
| 6 | Controller preserved (PMO45DataSourceController) | ✅ | 9 endpoints; no password echo in response bodies |
| 7 | Response body shape | ✅ | `ApiResponse {code, message, data, timestamp, success}`; graceful-degraded (no 500) — except P2-01 raw message leak |

## 4 forbidden boundary score

| # | 禁止动作 | 结果 | 证据 |
|---|---|---|---|
| F-01 | 改 `AgentMetricsService.java` | ✅ not Wave-2 | mtime `2026-09-06 22:49`; diff = PMO-38 content |
| F-02 | 改 `OntologyWorkflowController.java` | ✅ not Wave-2 | mtime `2026-09-06 22:45` |
| F-03 | `git checkout` reset Wave-1 | ✅ working tree dirty as-expected | `git status` 225 modified / 33 untracked; 3 forbidden files carry PMO-38/40 fingerprints unrelated to datasource |
| F-04 | 新建 Maven 模块 (13 上限) | ✅ no new module | no new `pom.xml` in scope dirs |

## Acceptance criteria live-curl score

| Test | Expected | Actual | Status |
|---|---|---|---|
| B | POST ORACLE 201 + id | 201 + `c2a88c6bd601448fbc7ab3f137d81101` | ✅ |
| B-neg | POST invalid type 400 readable | 400 ValidationDetail "不支持的数据源类型: NOT_A_TYPE。当前支持: ..." | ✅ |
| E | POST MINIO (standard) 400 readable | 400 "标准版(standard)不支持 minio 对象存储数据源，请部署 enterprise/ultimate 版本" | ✅ |
| D | POST /test-connection/{oracleId} 200 + readable + 10s cap | 200 + `"JDBC 驱动未加载: oracle.jdbc.OracleDriver 不在 classpath ..."` in 50ms (prefails at Class.forName) | ✅ |
| F | POST /preview-schema oracle 200 graceful | 200 + empty lists + readable error | ✅ |
| A | GET /supported-types 200 + 9 | 200 + 9 types | ✅ |
| **G** | **`/api/datasource` (no v1) 200 — 双路径 requirement** | **404** | ❌ **P1-01** |

## Build verification (v7) — env-blocked, indirect pass

- `mvn` is `apachemaven-3.9.11` at `/mnt/d/JavaProjects/env/` (Windows D: drive, NOT a WSL-native Maven)
- WSL + `/mnt/d/` maven + relative parent-pom resolution → doubled UNC path (`\\wsl.localhost\Ubuntu\home\guorongxiao\ECOS\ecos_backend\wsl.localhost\Ubuntu\home\guorongxiao\ECOS\ecos_backend\pom.xml`) → build fails with "Non-readable POM"
- **Not a Wave-2 issue** — pre-existing env laying bug. Worker did **not** claim build verification, so no unmet claim.
- **Indirect compile-signal** (3 data points all consistent):
  1. `.m2 repository/com/chinacreator/gzcm/data-engine-impl/1.0.0-SNAPSHOT/data-engine-impl-1.0.0-SNAPSHOT.jar` mtime **`2026-09-08 13:11:38`** (new artifact from this work)
  2. Gateway PID 22852 started **`Tue Sep 8 13:06:01`** with old-dep stack that includes `data-engine-impl-1.0.0-SNAPSHOT.jar` on classpath
  3. Live `/supported-types` returns 9 types (only the new code has 7 additions including `ORACLE/MSSQL/DM/KINGBASE/GAUSS/MINIO/FILESYSTEM`) — **cannot answer 9 without the Wave-2 code**
- Verdict: **PASS-INDIRECT** (env-blocked)

## Gate output

```json
{
  "task_id": "t_e87feda5",
  "under_review": "t_1ab054fb",
  "pmo": "PMO-46 wave2 datasource multi-type batch",
  "round": 2,
  "reviewed_at": "2026-09-08T13:34:00+08:00",
  "deliverable_allowed": false,
  "is_partial": true,
  "p0_count": 0,
  "p1_count": 2,
  "p2_count": 2,
  "findings": [
    {"id": "P1-01", "severity": "P1", "status": "NEW", "component": "gateway filter + controller", "summary": "双路径 /api/datasource 404 (filter only rewrites /api/v1/datasource and /datasource); violates AGENTS conditional rule 2; SecurityConfig.permitAll pre-positioned but filter 404s first"},
    {"id": "P1-02", "severity": "P1", "status": "CARRY-FORWARD (Round-1 P1-01)", "component": "DataEngine-Impl DataSourceServiceImpl", "summary": "connectionConfig.password stored plaintext in PG; no security-engine integration; violates card claim '加密走 security-engine' + 架构铁律 §3; escalates to P0 under security audit"},
    {"id": "P2-01", "severity": "P2", "status": "NEW", "component": "DataSourceServiceImpl.previewSchema", "summary": "preview-schema Oracle error message 'The url cannot be null' leaks raw Java exception; should be Oracle-specific"},
    {"id": "P2-02", "severity": "P2", "status": "NEW", "component": "task-card vs implementation edge", "summary": "task cards rename 'new class' deliverables (VO/JdbcConnectionTester) to functional re-scope via existing classes; functionally OK; arch should reconcile terminology"}
  ],
  "round1_p0_p1_closures": [
    {"id": "P0-01", "status": "CLOSED", "via": "re-scoped to JdbcConnector.testConnectionDetailed"},
    {"id": "P0-02", "status": "CLOSED", "via": "re-scoped to DataSourceEntity + 9-value SUPPORTED_TYPES"},
    {"id": "P1-03", "status": "CLOSED", "via": "testMinio + testFilesystem now implemented (not empty stubs)"}
  ],
  "blockers_for_release": ["P1-01", "P1-02"],
  "expected_rework_time": "< 1 hour (P1-01: 1 file change; P1-02: 1 file change + SecurityClient inject)",
  "artifacts": [
    "/home/guorongxiao/ECOS/docs/swarm/kanban-gates/2026-09-08_pmo46-datasource-type-batch/wave2-round2-verification.md",
    "/home/guorongxiao/ECOS/docs/swarm/kanban-gates/2026-09-08_pmo46-datasource-type-batch/wave2-issue-verification/3-code-verification/055_verifier_t_e87feda5/wave2-gate-verdict.md"
  ]
}
```

**Release advice**: HOLD (can advise: no)

## 追加 Finding（13:40+）

**P1-02 响应路径扩展**：live `POST /api/v1/datasource` 201 响应体回显 `connectionConfig` plaintext `password`（见 `round2-evidence/curl/w2_oracle.json`）。P1-02 现含两层：**KMS**+**controller response redaction**。任一层未闭合，release 需追加安全审计。

