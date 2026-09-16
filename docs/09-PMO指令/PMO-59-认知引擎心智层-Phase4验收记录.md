# PMO-59 Phase 4 验收记录 — 认知引擎心智层：人机干预与审计（P4a 告警落库 + P4b 前端干预面板）

> 来源: PM项目经理（PMO-59 P4 收口）
> 日期: 2026-09-16
> 责任人: fullstack-implementer（开发实现）/ PM项目经理（验收）/ code-artifact-reviewer（独立审查）
> 铁律: 继承 [架构铁律](../../.trae/rules/架构铁律.md)（§2.4 安全接入 / §2.5 监控告警补强而非自建 / §5.1 禁止清单 / §4 前端铁律）+ Phase 4 指令 §禁止清单；commit hash 即 DONE 凭证
> 前序: P1/P2/P3 全部 PASS 并合入 `release/v2.1-alpha`；本 Phase 起始 HEAD = `e480988`
> 硬门禁: P4a 未过验收（编译绿 + curl/psql 通 + reviewer PASS）不得启动 P4b

---

# ══ P4a 段：后端告警落库通道 ══

## 0. 交付物清单（P4a T1~T5）

| Task | 交付文件 | 状态 |
|:--|------|:--:|
| T1 | `ecos_backend/gateway/src/main/resources/db/migration/V131__ecos_warn_log.sql`（新表 `ecos_warn_log`：id / log_id(uniq 幂等) / warn_type / warn_level / warn_objid / warn_objname / warn_message / **fault_context JSONB** / **review_tag** / warn_hand / warn_result / ishanded / warn_time + 审计六列 + is_deleted；1 uniq + 4 普通索引；**只加不删**，编号 = 现有最大 V130 + 1 = V131） | ✅ |
| T2 | `runtime/runtime-monitor`：`IWarnLogService.java`（新增七参 `warn(...)` **default** 方法，默认委托既有五参 → 签名只增不改）+ `WarnLogServiceImpl.java`（保留内存 `logStore`，**只增持久化分支**；`JdbcTemplate` 构造器注入，null=纯内存态向后兼容；落库/序列化异常一律 try-catch WARN 不抛出）+ `WarnLogBean.java`（新增 `warn_level`/`fault_context`/`review_tag` 三字段，跟齐既有手写 getter/setter 风格） + `runtime-monitor/AGENTS.md`（新表/落库通道/查询口径登记） | ✅ |
| T3 | 勘察裁定：既有只读告警端点 2 个（`GET /api/monitor/alerts` 统计 / `GET /api/v1/alerts` 列表，源 `ecos_alert_history`）**已存在** → **不新增 REST 端点**（`ecos_warn_log` 走 psql 直查 + 复盘 `warnAlerts` 段），结论登记 `runtime-monitor/AGENTS.md` | ✅ |
| T4 | `cognitive-engine-impl/.../service/mental/MentalReviewService.java`（复盘输出**只增键** `warnAlerts[]` + `summary.warnAlerts`，按 `review_tag` 与复盘 tag **同源等值** + since 只读查询；V131 未建表时降级空列表不阻断其余段落）+ `CognitiveHypothesisService.java`（失效告警改走七参重载，携带 faultContext + REVIEW_TAG）+ `CognitiveMentalConfig.java`（装配时补 `ObjectProvider<JdbcTemplate>`，缺失退化为纯内存态仅 WARN） | ✅ |
| T5 | `docs/plans/api-contract.md` §3.4 回填（P4a 段：告警落库通道 + 复盘只增键 + 零新增端点裁定）+ 本验收记录 | ✅ |

## 1. V2 集成点 grep（编译前逻辑校验）

| 检项 | 结论 |
|------|------|
| 依赖方向（铁律 §2.5 / 指令禁止清单 10） | `runtime-monitor/pom.xml` 仅 `runtime-core` + `runtime-access` 两项依赖；**0 处 `engine/*`、`services/*`、`workspace/*`** → runtime-monitor 零反向依赖 ✅ |
| 补强而非自建（§2.5.6） | 落库增强写在 runtime-monitor `WarnLogServiceImpl` 内（非引擎内自建监控），引擎侧仅经 `IWarnLogService` 契约调用 ✅ |
| LLM 零参与（指令禁止清单 3） | `WarnLogServiceImpl` / `IWarnLogService` / `MentalReviewService` / `CognitiveMentalConfig` 全文 grep `llm|openai|deepseek|prompt|chatcompletion` = **0 命中** ✅ |
| Store/SQL 规范 | 变更文件 grep（忽略大小写）`select *` = **0 命中**（唯一命中为注释文字"0 SELECT *"）；全部走显式列名 + `is_deleted = 0` 过滤 ✅ |
| API 只增不改 | `IWarnLogService` 既有 5 方法签名与语义 0 变更（新增方法为 `default`，既有实现零破坏）；`mental-reviews` 响应仅追加 `warnAlerts`；0 处既有路径/参数签名变更 ✅ |
| DDL 只加不删 | V131 仅 `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`，0 触碰既有表/列 ✅ |
| 安全集成强制卡（§2.4 #8 计数） | grep `encrypt|decrypt|SecretService|IDataEncryptionService|securityEngine` = **0 命中**；**判定理由**：本批次落库字段为告警正文 + 结构化故障上下文（`hypothesisId/domain/autoDetected/invalidAt/reviewTag`），**不含凭据、密钥、Token、手机/邮箱/身份证等敏感数据**，不触发 §2.4 #8 加密/脱敏三类强制项 → 0 命中属合规而非漏项 ✅ |
| 审计（§2.4 #5） | 告警留痕写属**可观测性数据**（旁路留痕），业务写操作 `hypothesis.invalidate` 仍走既有 `ecos.audit`（`audit("hypothesis.invalidate", ...)` 未改）；本批次 0 新增业务写路径 ✅ |

## 2. V3 全量编译（mvn install 非 compile）

编译前已核 8080 无旧 gateway 监听（`Get-NetTCPConnection -LocalPort 8080` 空），无并发用户构建。

```
命令: & "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f ecos_backend/pom.xml clean install "-Dmaven.test.skip=true" -B
结果: BUILD SUCCESS — 49 模块全部 SUCCESS，Total time: 08:57 min（Finished at 2026-09-16T22:48:49+08:00）
      gateway 尾部：ECOS Gateway SUCCESS [18.129 s]；产物 gateway-1.0.0-SNAPSHOT.jar 147,558,074 B @22:48:48
```

## 3. V4 Gateway 启动 + curl/psql 验收（enterprise profile）

启动（本检出无 `_win_tasks/`，用等价命令；**JWT 注入格式实证更正**）：

```powershell
$pem = Get-Content 'C:\Users\guoro\.config\ecos\jwt-private-key.pem' -Raw
$b64 = (($pem -replace '-----BEGIN PRIVATE KEY-----','') -replace '-----END PRIVATE KEY-----','') -replace '\s',''
java "-DJWT_PRIVATE_KEY=$b64" -Xms512m -Xmx2g -jar gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise
```
> ⚠️ **实证更正**：直接传 PEM（把换行替换为 `\n` 字面量）会触发
> `java.lang.IllegalArgumentException: Illegal base64 character 5c`（`JwtTokenProvider.decodePemOrBase64` 的
> `replaceAll("\\s","")` 不剥离 `\n` 两字符字面量）→ **必须传 PEM body 的纯 base64（无头尾无换行，1624 字符）**。
> 本次注入 `JWT body chars: 1624`，启动成功。

日志关键行（GBK 控制台编码下抓取，语义还原）：
```
[CognitiveMental] IWarnLogService 已装配 + 告警落库通道启用 (ecos_warn_log, PMO-59 P4a)
Started GatewayApplication in 51.384 seconds (process running for 53.477)   ← PID 20516
```
非匿名端点鉴权回归：`GET /api/monitor/health` 无 token → **403**（默认 DENY 语义未破坏）。

### 3.1 T1 验收：DDL 执行 + 结构核对（docker cp + psql -f 手工执行）

```
$ docker exec ecos-postgres psql -U postgres -d sys_man -c "\d ecos_warn_log"
                                   Table "public.ecos_warn_log"
    Column     |            Type             | Nullable |           Default
---------------+-----------------------------+----------+-----------------------------
 id            | character varying(64)       | not null |
 log_id        | character varying(64)       | not null |
 warn_type     | character varying(64)       |          |
 warn_level    | character varying(16)       | not null | 'WARN'::character varying
 warn_objid    | character varying(128)      |          |
 warn_objname  | character varying(256)      |          |
 warn_message  | text                        |          |
 fault_context | jsonb                       |          |
 review_tag    | character varying(64)       |          |
 warn_hand     | character varying(64)       |          |
 warn_result   | text                        |          |
 ishanded      | character varying(4)        | not null | '0'::character varying
 warn_time     | timestamp without time zone | not null | now()
 create_time   | timestamp without time zone | not null | now()
 update_time   | timestamp without time zone | not null | now()
 create_by     | character varying(64)       |          | 'system'::character varying
 update_by     | character varying(64)       |          | 'system'::character varying
 is_deleted    | smallint                    | not null | 0
Indexes:
    "ecos_warn_log_pkey" PRIMARY KEY, btree (id)
    "idx_ecos_warn_log_objid" btree (warn_objid)
    "idx_ecos_warn_log_review_tag" btree (review_tag)
    "idx_ecos_warn_log_time" btree (warn_time DESC)
    "idx_ecos_warn_log_type" btree (warn_type)
    "uniq_ecos_warn_log_log_id" UNIQUE, btree (log_id)
```
表存在 + 1 uniq + 4 普通索引就绪 ✅

### 3.2 T2 验收：告警写入 → 落库含 fault_context/reviewTag

触发链路（实测走真实业务端点，非 mock）：登录 → 注册 VALID 假设 → 人工失效。
```
POST /api/v1/auth/login {"username":"admin","password":"admin123"}   → code:0, accessToken(len=615)
POST /api/v1/cognitive/hypotheses {"hypothesisCode":"HYP-P4A-VERIFY-001","statement":"P4a 告警落库通道验收假设","domain":"pricing","metricRef":"competitor_price_cut_prob","tenantScope":"default"}
  → {"code":0,...,"id":"cog_hyp_7e5ecdb1-bd9","status":"VALID"}
POST /api/v1/cognitive/hypotheses/cog_hyp_7e5ecdb1-bd9/invalidate {"reason":"PMO-59 P4a 验收：人工失效触发告警落库"}
  → {"code":0,...,"status":"INVALIDATED","invalidAt":"2026-09-16T22:52:03.883181"}
```
```
$ docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT log_id, warn_type, warn_level, warn_objid, warn_objname, review_tag, warn_hand, ishanded, fault_context->>'reviewTag' AS ctx_review_tag, fault_context->>'autoDetected' AS ctx_auto FROM ecos_warn_log ORDER BY warn_time DESC LIMIT 3;"
      log_id       |            warn_type             | warn_level |      warn_objid      |      warn_objname      |       review_tag        |  warn_hand  | ishanded |     ctx_review_tag      | ctx_auto
-------------------+----------------------------------+------------+----------------------+------------------------+-------------------------+-------------+----------+-------------------------+----------
 log_1789570323913 | COGNITIVE_HYPOTHESIS_INVALIDATED | WARN       | cog_hyp_7e5ecdb1-bd9 | cognitive-mental-layer | P2b-mental-layer-review | mental-scan | 0        | P2b-mental-layer-review | false
```
**1 行落库，`fault_context` JSONB 含 `reviewTag`，`review_tag` 列同源写入** ✅

### 3.3 T2 降级验收：落库不可用时不阻塞主流程

故障注入（可逆）：`ALTER TABLE ecos_warn_log RENAME TO ecos_warn_log_bak` → 触发失效 → 观察响应与日志 → 还原表名。
```
POST /api/v1/cognitive/hypotheses/cog_hyp_aae8fc3f-9b9/invalidate {"reason":"PMO-59 P4a 降级路径验收：落库表不可用"}
  → {"code":0,"message":"ok",...,"status":"INVALIDATED"}     ← 业务未被阻塞（HTTP 200 + code 0）✅

日志: WarnLogServiceImpl : 告警落库失败 (ignored, 不阻塞主流程):
      logId=log_1789570362254 type=COGNITIVE_HYPOTHESIS_INVALIDATED
      err=PreparedStatementCallback; bad SQL grammar [INSERT INTO ecos_warn_log ...]   ← WARN 级、不抛出 ✅

还原后 $ SELECT count(*) FROM ecos_warn_log → 1（降级期间 0 行写入，无脏数据）✅
```

### 3.4 T3 验收：0 个新增端点 / 既有告警端点回归不变

`git grep -n "warn-logs"` 全仓 0 命中 → **未新增 warn 专用端点**（勘察裁定，指令 §实现决策 5）；
`GrepAlertController` 类注声明的 `:8080` 降级路由行为未触碰；`GET /api/monitor/health` 无 token 仍 403（鉴权语义未松动）✅

### 3.5 T4 验收：复盘输出只增键 + 既有结构 0 变化

```
GET /api/v1/cognitive/mental-reviews?tag=P2b-mental-layer-review&since=2026-09-16T00:00:00   (带 token, HTTP 200)

MR_KEYS: tag,since,reconstructionMode,beliefTimelines,hypotheses,evidence,runImpacts,warnAlerts,summary
      ← 既有 8 键全保留，新增第 9 键 warnAlerts（只增）✅
MR_SUMMARY: {"beliefVariables":0,"beliefVersions":0,"hypothesesTotal":1,"hypothesesInvalidated":1,
             "evidenceCount":0,"runsSuperseded":0,"warnAlerts":1}
      ← 既有 6 计数键 0 变化，新增 warnAlerts:1 ✅
MR_FIRST_WARN: {"id":"warn_f41114b3fae84004","logId":"log_1789570323913",
                "warnType":"COGNITIVE_HYPOTHESIS_INVALIDATED","warnLevel":"WARN",
                "warnObjId":"cog_hyp_7e5ecdb1-bd9","warnObjName":"cognitive-mental-layer",
                "warnMessage":"认知假设失效: HYP-P4A-VERIFY-001（P4a 告警落库通道验收假设）autoDetected=false | faultContext={...reviewTag=P2b-mental-layer-review}",
                "faultContext":{"phase":"hypothesis-invalidation","domain":"pricing","invalidAt":"2026-09-16T22:52:03.883181",
                                "reviewTag":"P2b-mental-layer-review","autoDetected":false,"hypothesisId":"cog_hyp_7e5ecdb1-bd9"},
                "reviewTag":"P2b-mental-layer-review","warnTime":"2026-09-16T22:52:03.913"}
      ← faultContext 已解析为 JSON 对象（非字符串）✅
```
> 说明：`beliefVariables/beliefVersions/evidenceCount = 0` 系 `since=2026-09-16T00:00:00` 时间下界过滤（三表行均为 09-13/09-14 写入），非通道缺陷。

### 3.6 P3 端点回归（P4b 数据源前置核验）

```
GET  /api/v1/cognitive/beliefs?domain=pricing                       → code:0, 4 行（v1~v4；v4 manualOverride=true）
GET  /api/v1/cognitive/beliefs/competitor_price_cut_prob/1/replay?domain=pricing&sampleCount=200&seed=42
     → code:0, replayedVersion=1, currentVersion=4,
       believedDistribution=[{none,0.7},{mild,0.25},{aggressive,0.05}]（与 v4 分布不同 → 版本切换可见差异）,
       riskMetrics={expectedBenefit:0,maxDrawdown:0,lossProbability:0,volatilityRange:[1.0,2.0]}
POST /api/v1/cognitive/counterfactual {domain:pricing, DELTA +0.5, N=200, seed=42}
     → code:0, expectedBenefit=0.5（DELTA 严格平移）, volatilityRange=[1.5,3.5], sensitivity=0.139,
       assumptionRefs=0, excludedAssumptions=1（刚失效的 HYP-P4A-VERIFY-001 入排除）✅ 既有行为 0 破坏
```

## 4. code-artifact-reviewer 独立审查结论（P4a）

| 审查项 | 结论 | 证据 |
|:--|:--|:--|
| §2.4 安全集成计数 | **PASS（0 命中属合规）** | 变更文件 `encrypt\|decrypt\|SecretService\|IDataEncryptionService\|securityEngine` = 0；落库字段不含凭据/密钥/PII，不触发 §2.4#8 三类强制项 |
| 命名规范 | **PASS** | 新类成员 camelCase；`WarnLogBean` 新字段沿用该 bean 既有 snake_case 约定（`fault_context`/`review_tag`/`warn_level`），与文件内既有字段一致 |
| 依赖方向 | **PASS** | runtime-monitor POM 仅 runtime-core/runtime-access；0 处 engine/services/workspace 依赖或 import |
| 只增不改 | **PASS** | 接口新增 `default` 重载（既有 5 方法零变更）；响应仅追加键；DDL 仅新增表/索引 |
| 补强而非自建（§2.5.6） | **PASS** | 落库增强在 runtime-monitor 内；引擎侧仅经契约调用 |
| 降级健壮性 | **PASS** | 落库/序列化异常 try-catch WARN；实证表缺失时业务返回 code 0 |
| 日志规范 | **PASS** | 全部 WARN 级带 logId/type/err 定位；0 处打印敏感数据 |
| 前端五铁律 | N/A（P4a 无前端变更） | — |

**审查裁定：PASS（deliverable_allowed = true）** → 放行 P4b。

## 5. P4a commit 凭证

```
4c5ffc4  feat(认知引擎): PMO-59-P4a 告警日志落 PG 关闭内存态残留风险   （8 files changed, 327 insertions(+), 24 deletions(-)）
`git log --grep "PMO-59-P4a"` 可溯源
```
> clean commit 校验：仅暂存本批次 8 文件；工作区既有非本批次改动（`docs/plans/merge-audit-v2.1-alpha.md`、
> `ecos_frontend/src/pages/data-quality/*`）与第三方任务产物（`docs/03开发阶段/03-03-审查报告/t_2c9e5b71_*`）**未提交**；
> 0 处 `git add -A`。

---

# ══ P4b 段：前端人机干预面板 ══

（P4a 验收 PASS 后执行，见下）
