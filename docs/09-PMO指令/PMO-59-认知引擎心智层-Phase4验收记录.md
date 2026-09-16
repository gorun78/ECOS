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

> 启动前置：P4a 硬门禁已满足（编译绿 4c5ffc4 / curl+psql 通 / reviewer PASS）。

## 0. 交付物清单（P4b T1~T5）

| Task | 交付文件 | 状态 |
|:--|------|:--:|
| T1 | `ecos_frontend/src/pages/scenario/CounterfactualTab.tsx`（347 行；「仿真推演」：域/主变量/采样次数/seed + 干预项列表（变量 + SET/DELTA + 量，可增删）→ `POST /api/v1/cognitive/counterfactual` → 渲染 `scenarioSummary` + 四指标卡（期望收益/最大回撤/亏损概率/波动区间 p05~p95）+ 敏感性 Top3 + `assumptionRefs` 芯片 + `excludedAssumptions` 列表） | ✅ |
| T2 | `ecos_frontend/src/pages/scenario/ReplayTab.tsx`（364 行；「时间回放」：域 → 版本链下拉（`GET /api/v1/cognitive/beliefs?domain=` 按变量分组、版本降序、切变量自动带最新版）+ 可选 do(A) 干预 → `GET /api/v1/cognitive/beliefs/{variable}/{version}/replay` → 渲染 `replayedVersion`/`currentVersion`/`assumptionsValidAtReplayTime`/`versionUpdatedAt` + `believedDistribution` 概率条 + 四指标对比） | ✅ |
| T3 | `ecos_frontend/src/pages/scenario/OverrideTab.tsx`（332 行；「人工覆写」：域 → 目标变量（最新版本预填分布）→ 分布草稿编辑（Σprob 实时校验，容差 1e-6 与后端同口径）+ 覆写理由必填 + 可选证据 id → `POST /api/v1/cognitive/beliefs/{variable}/override`；**二次确认走项目既有 `components/common/ConfirmDialog`**（`variant="warning"`，非 `window.confirm`/`alert` 裸用）；成功后重拉版本链标记「人工覆写」+ toast） | ✅ |
| T4 | `ecos_frontend/src/pages/scenario/MentalReviewTab.tsx`（286 行；「假设失效告警 + 复盘」：tag + since(datetime-local) → `GET /api/v1/cognitive/mental-reviews` → 渲染 `warnAlerts`（告警级别/类型/对象/时间/`faultContext`/`reviewTag`）+ 假设时间线（失效红/有效绿徽章）+ 心智版本时间线（含人工覆写标记）+ 作废 run 留痕（自动/人工徽章）+ `summary` 计数；非法 tag 由后端 400 → error toast，空态有 i18n 文案） | ✅ |
| 共享 | `ecos_frontend/src/pages/scenario/mentalTypes.ts`（162 行；推演/回放/复盘共享 TS 类型 + `formatNum`/`formatTime`，避免多面板重复声明） | ✅ |
| 入口 | `ecos_frontend/src/pages/scenario/CognitionPanel.tsx`（698 → **737 行**，仍 ≤800 铁律）：新增 5 子视图切换条（认知诊断/仿真推演/时间回放/人工覆写/告警与复盘），**既有诊断子视图以 `hidden` 容器包覆、0 逻辑改动**（挂载态保留，避免重拉与状态丢失） | ✅ |
| T4(i18n) | `ecos_frontend/src/locales/scenario/zh-CN.json` + `en.json`（各新增 **69 个 `scenario.iv.*` 键**，中英双写；namespace `scenario` 既有注册，无需改 LanguageContext） | ✅ |
| T5 | `npm run lint` 绿 + 组件级验证（见 §2）+ `npm run build` 绿 + 本记录 | ✅ |

> ⚠️ **路径勘误（P4b 实施发现）**：Phase 4 指令表内写的 i18n 路径 `ecos_frontend/src/i18n/locales/scenario/` 不存在，
> 实际路径为 **`ecos_frontend/src/locales/scenario/`**（扁平 key 风格，如 `"scenario.cog.title"`）。本单按实际路径落地（既有文件，只增键）。
> ⚠️ **API 勘误（沿用 P2b 已登记口径）**：人工覆写实际为 **`POST /api/v1/cognitive/beliefs/{variable}/override`**（非指令表中 `PUT .../{id}/override`），
> 与 `docs/plans/api-contract.md` §3.4 预登记一致（API 只增不改）；前端按实际实现消费。

## 1. V2 集成点 grep（编译前逻辑校验）

| 检项 | 结论 |
|------|------|
| 前端铁律·主题色 | 5 个新增文件 `bg-white\|bg-slate-9\|bg-gray-9\|bg-zinc-9\|border-gray-\|text-slate-8` 命中 **0**；`CognitionPanel.tsx` 本批次新增行同项命中 **0**（新徽章改走 `styles.warningBg/warningText/warningBorder`、`success*`、`danger*` 主题语义 token）✅ |
| 前端铁律·i18n | 5 个新增文件「非注释中文行」命中 **0**（全部 `t('scenario.iv.*')`）；新增 69 键中英双写 ✅ |
| 前端铁律·图标 | 新增文件 `<svg` 命中 **0**；统一 `LucideIcon`（lucide-react）✅ |
| 前端铁律·文件行数 | 347 / 364 / 332 / 286 / 162 / 737 行 → 全部 ≤ 800 ✅ |
| 前端铁律·Tab 独立文件 | 推演/回放/覆写/复盘 = 4 个独立文件 + 1 共享类型文件；`CognitionPanel` 仅做容器与子视图切换 ✅ |
| API 调用规范 | 统一走 `src/api.ts` 的 `apiFetchData`（自带 token/401/403/网络降级）；0 处裸 `fetch`；0 处硬编码地址 ✅ |
| LLM 零参与 | 前端 0 处 LLM 调用；全部只读/受控写端点渲染 ✅ |
| 写后刷新（契约铁律 §4.8-3） | OverrideTab 覆写成功后 `loadBeliefs(domain)` 重拉版本链（测试断言 `beliefs?` 调用 ≥2 次）✅ |

## 2. V3 前端编译门 + 组件级验证

### 2.1 tsc 编译门（铁律 §5.1#11）

```
$ npm run lint          # = tsc --noEmit
> react-example@0.0.0 lint
> tsc --noEmit
（无输出） → EXIT=0 ✅
```

### 2.2 组件级验证（浏览器 E2E 降级路径）

> **环境事实（须登记）**：本执行环境**未暴露浏览器工具**（无 `Task` 子代理工具、无 `run_mcp`/`browser_*` 工具集），
> 按 Phase 4 指令 §环境要点允许的降级路径（"记录该事实并至少完成 lint + 构建 + 组件级验证"）执行；
> 前端 E2E 的「4 主题切换 + 中英切换 + console 0 error + network 无意外 4xx/5xx」四项**未在真实浏览器中实测**，
> 其中前三项由主题/i18n token 化改造 + 组件渲染测试 + 生产构建间接覆盖，列为残留风险（见 §5）。

项目已具备 `vitest + @testing-library/react + jsdom` 基建（既有 `GitVersionPanel.test.tsx` 等同款），
故按项目既有惯例补 **`ecos_frontend/src/pages/scenario/CognitionIntervention.test.tsx`**（6 case，Mock `apiFetchData`）：

```
$ npx vitest run src/pages/scenario/CognitionIntervention.test.tsx
 RUN  v4.1.10 D:/workspace/javaprojects/ECOS/ecos_frontend
 ✓ src/pages/scenario/CognitionIntervention.test.tsx (6 tests) 2187ms
     ✓ 输入业务域 → 加载版本链；选版本回放 → GET replay 渲染版本对比与分布快照  766ms
     ✓ 选变量 → 覆写提交走 ConfirmDialog 二次确认 → POST override + 写后重拉列表标记人工覆写  885ms
 Test Files  1 passed (1)
      Tests  6 passed (6)
   Duration  33.43s
```

断言覆盖（与 T1~T4 验收口径逐条对齐）：
1. CounterfactualTab 必填校验拦截（不发请求）→ 通过；
2. CounterfactualTab 请求体 = `{domain, variableName, sampleCount:200, seed:42, interventions:[{...op:'SET',value:0.5}]}`（断言 `url=/api/v1/cognitive/counterfactual` + `method=POST`），渲染出 `0.5000`（期望收益）/`0.2500`（最大回撤）/`0.1300`（亏损概率）/`[1.5000, 3.5000]`（波动区间）/敏感性 `0.1390`/`assumptionRefs` 芯片/`excludedAssumptions`（含 `INVALIDATED`）→ 通过；
3. ReplayTab：域输入后版本链加载（下拉含 `price_cut_prob`）→ 切变量自动带最新版 `v3` → 切 `v1` 回放，断言 `url` 含 `/api/v1/cognitive/beliefs/price_cut_prob/1/replay?domain=pricing`，渲染 `v1` vs `v3` 与 `none`/`aggressive` 分布点 → 通过（版本切换差异可见）；
4. OverrideTab：Σprob=0.6 时校验拦截（不弹框）→ 修至 1 + 填理由 → 弹 `确认人工覆写`（`ConfirmDialog`）→ 确认后断言 `url=/api/v1/cognitive/beliefs/price_cut_prob/override`、body `{domain, overrideReason, discreteDistribution:[{outcome:'high',prob:1}]}`、`beliefs?` 被调用 ≥2 次（写后刷新）、渲染「人工覆写」标记 → 通过；
5. MentalReviewTab：断言 `url` 含 `/api/v1/cognitive/mental-reviews?tag=P2b-mental-layer-review`，渲染 `COGNITIVE_HYPOTHESIS_INVALIDATED` / `reviewTag` / `cognitive-mental-layer` / `HYP-001` / `INVALIDATED` / 作废 run `run-1` / 「自动检测」/ `warnAlerts` 计数 → 通过；
6. CognitionPanel 子视图切换：5 个入口全就位，点「仿真推演」→ 渲染推演表单（`干预项` + `敏感性 Top3`）→ 通过。

### 2.3 Vite transform 实测（dev server 真机拉取）

```
$ npm run dev      # tsx server.ts, :3000
src/pages/scenario/CounterfactualTab.tsx   HTTP=200 bytes=69184  transformError=False
src/pages/scenario/ReplayTab.tsx           HTTP=200 bytes=69189  transformError=False
src/pages/scenario/OverrideTab.tsx         HTTP=200 bytes=57060  transformError=False
src/pages/scenario/MentalReviewTab.tsx     HTTP=200 bytes=59973  transformError=False
src/pages/scenario/mentalTypes.ts          HTTP=200 bytes=6912   transformError=False
src/pages/scenario/CognitionPanel.tsx      HTTP=200 bytes=129149 transformError=False
src/locales/scenario/zh-CN.json            HTTP=200 bytes=12870  transformError=False
src/locales/scenario/en.json               HTTP=200 bytes=12964  transformError=False
```
（判定式含 `Transform failed|Internal server error|Pre-transform error` 三模式扫描，全部 False；页面根 `GET /` = 200）

### 2.4 生产构建（白屏级前置拦截）

```
$ npm run build     # vite build + esbuild server → dist/
✓ built in 3m 16s
dist/assets/ScenarioManagementView-BDTeHuY7.js   134.78 kB │ gzip: 31.41 kB   ← 含本单 4 个面板
dist\server.cjs 7.4kb / server.cjs.map 14.0kb / Done in 43ms
```
（>500 kB chunk 体积告警为既有基线，非本单引入。）

## 3. code-artifact-reviewer 独立审查结论（P4b）

| 审查项 | 结论 | 证据 |
|:--|:--|:--|
| 前端五铁律（主题色/i18n/图标/行数/独立 Tab） | **PASS（0 命中）** | 见 §1 逐项计数表；新徽章全部用 `useTheme().styles` 语义 token |
| i18n 双写完整性 | **PASS** | zh-CN.json / en.json 各 +69 键，键集合一一对应；组件内 0 硬编码中文 |
| 契约一致性（§4.8） | **PASS** | 端点/入参/枚举（`op: 'SET' \| 'DELTA'`、`domain` 必填、`discreteDistribution[{outcome,prob}]`）与后端 DTO 同源；写作操作写后刷新 |
| 二次确认规范 | **PASS** | 复用 `components/common/ConfirmDialog`（`variant="warning"`）；未引入 `window.confirm` |
| 异常与边界 | **PASS** | 所有 `apiFetchData` 调用 try/catch + toast；列表/枚举做空值兜底（`?? []`）；debounce 定时器在 `useEffect` 返回清理函数中 `clearTimeout`（无内存泄漏） |
| 冗余与抽象 | **PASS** | 共享类型/格式化抽 `mentalTypes.ts`；重复的指标卡抽组件内部小函数（`MetricBox`/`ReplayMetric`）；无重复面板实现 |
| 依赖方向/新增依赖 | **PASS** | 0 新增前端依赖（全部复用既有 `apiFetchData`/`LucideIcon`/`useTheme`/`useLanguage`/`ConfirmDialog`/`showToastGlobal`） |

**审查裁定：PASS（deliverable_allowed = true）**

## 4. P4b commit 凭证

```
401d1aa  feat(场景工作台): PMO-59-P4b 新增心智层人机干预面板（推演/回放/覆写/告警复盘）
         （9 files changed, 2121 insertions(+), 2 deletions(-)）
`git log --grep "PMO-59-P4"` 可溯源
```
> clean commit 校验：仅暂存本批次 10 文件（4 面板 + 共享类型 + 组件测试 + CognitionPanel + 2 locale + 本记录）；
> 非本批次改动（`docs/plans/merge-audit-v2.1-alpha.md`、`ecos_frontend/src/pages/data-quality/*`、`docs/03开发阶段/…/t_2c9e5b71_*`）**未提交**；0 处 `git add -A`。

## 5. 残留风险（Phase 4 收口）

| # | 风险 | 影响 | 处置建议 |
|:--|------|------|------|
| 1 | **真实浏览器 E2E 未执行**（本环境无浏览器工具）：4 主题切换 / 中英切换 / console 0 error / network 无意外 4xx/5xx 未实测 | 中 | 下次具备浏览器工具时对 `http://localhost:3000/#/project_workbench`（登录 admin/admin123）补跑：切 4 主题截屏 + 切 en 断言文案 + console/network 抓包 |
| 2 | `ecos_warn_log` 无 REST 查询端点（P4a T3 勘察裁定"不新增"） | 低 | 告警排障依赖 psql 直查或复盘 `warnAlerts` 段；若后续需要独立告警中心，按三滤波器判据另行评审新增 `/api/v1/monitor/warn-logs` |
| 3 | 复盘 `warnAlerts` 关联键 = `review_tag` 等值（单一 tag `P2b-mental-layer-review`） | 低 | 后续多 tag 场景需扩展 `MentalReviewService.REVIEW_TAGS` 白名单（既有机制，只增字符串） |
| 4 | 前端子视图以 `hidden` 保留诊断面板挂载态 | 极低 | 诊断面板 `useEffect` 仍会在切走后保持已加载数据（无额外请求风暴；如需卸载改为条件渲染即可） |
| 5 | Phase 4 指令表的两处描述偏差（i18n 路径、override 方法/路径）已在 §0 勘误登记 | 低 | 指令文档为 PMO 历史留痕，勘误以本验收记录为准（api-contract 预登记口径未变） |
| 6 | 未新增自动化集成测试（后端告警落库链路靠 curl+psql 手工验收） | 低 | 可在后续补 `WarnLogServiceImpl` 单测（需 JdbcTemplate mock）纳入 CI |

## 6. Phase 4 commit 列表（`git log --grep "PMO-59-P4"` 可溯源）

| # | commit | 内容 |
|:--:|:--|:--|
| 1 | `4c5ffc4` | `feat(认知引擎): PMO-59-P4a 告警日志落 PG 关闭内存态残留风险`（V131 + runtime-monitor 落库分支 + 复盘只增键 + 6 文件，8 files / +327 / −24） |
| 2 | `8ad4b0e` | `docs(认知引擎): PMO-59-P4a 回填指令契约与验收记录`（api-contract §3.4 + Phase4 指令 + 本记录 P4a 段） |
| 3 | `401d1aa` | `feat(场景工作台): PMO-59-P4b 新增心智层人机干预面板（推演/回放/覆写/告警复盘）`（4 面板 + 共享类型 + 组件测试 + CognitionPanel 子视图 + i18n 双写，9 files / +2121 / −2） |
| 4 | `（本记录 P4b 段所在提交）` | `docs(场景工作台): PMO-59-P4b 补验收记录与审查结论` |

---

# ══ PMO-59 全四阶段收口总结 ══

## 交付链闭环（P0 → P4）

| 阶段 | 交付 | commit 凭证 | 状态 |
|:--|------|:--|:--:|
| P0/P1 | 数据契约：三表 DDL `V127/V128/V129` + common-api VO（`EvidenceRecordVO`/`HypothesisVO`/`BeliefDistributionVO`）+ `cognitive-engine-api` 双接口（`IUncertaintyJudgementService`/`IHypothesisLifecycleService`）；术语定稿（"不确定性判断"/"仿真情景"） | 见 Phase1 验收记录 | ✅ |
| P2 | 三表 Store/DTO/Controller（9 端点）+ 贝叶斯更新 + 人工覆写 + Kafka `ecos.cognitive`（groupId `dccheng-cognitive-group`）+ runtime-task `COGNITIVE_MENTAL_SCAN` 定时补算 + 双规则假设失效检测 + 告警（内存态） | `a1ac61d` / `6b23a76`（P2b） | ✅ |
| P3a | 反事实推演 `POST /api/v1/cognitive/counterfactual`（do(A) SET/DELTA + 蒙特卡洛四指标 + 敏感性 Top3 + 假设有效性过滤；纯 Java、seed 可复现） | 见 Phase3 验收记录 | ✅ |
| P3b | 时间回放 `GET .../beliefs/{variable}/{version}/replay` + `CognitiveInvalidationConsumer`（失效→作废 run，V130）+ workspace `SAFEGUARD` run 类型 + 复盘聚合 `GET .../mental-reviews` | 见 Phase3 验收记录 | ✅ |
| **P4a** | **告警落库通道**：`ecos_warn_log`（V131）+ `IWarnLogService` 七参重载 + 落库分支（降级不阻塞）+ 复盘 `warnAlerts` 只增键 | `4c5ffc4` / `8ad4b0e` | ✅ |
| **P4b** | **前端人机干预面板**：推演/回放/覆写/告警复盘 4 独立 Tab + CognitionPanel 子视图入口 + i18n 双写 + 组件级验证 | 见 §6 #3/#4 | ✅ |

## 阶段净收益

1. **闭合 P2b 残留风险 #1**：认知失效告警由「进程重启即失」升级为 PG 持久化（幂等 `log_id` uniq + `fault_context` JSONB + `review_tag` 复盘键），周一故障复盘首次具备**可查证**的告警留痕。
2. **闭合 P2b/P3 残留风险 #4**：复盘口径由「三表版本链 + V130 impact」扩展为「+ 告警维度」（`warnAlerts`），事件口径与告警口径**同源等值**（同一 `REVIEW_TAG`），无双口径歧义。
3. **心智层首次具备人机干预 UI 入口**：P3a/P3b 的只读能力（推演/回放/覆写/复盘）从"仅 curl 可达"变为"场景工作台可操作"，形成「诊断 → 推演 → 回放 → 专家覆写 → 告警复盘」闭环。
4. **架构约束零违反**：runtime-monitor 零反向依赖、告警落库补强而非自建、LLM 零参与认知计算、API 只增不改、schema 只加不删、前端五铁律 0 命中。

## 后续建议（优先级排序）

1. **P5 候选（浏览器 E2E 补跑）**：具备浏览器工具时补 4 主题 × 中英切换 × console/network 三查（本 Phase 唯一未实测项），并纳入 `ecos-tests/` 冒烟脚本。
2. **告警中心独立化（按需）**：若产品侧需要独立「告警中心」页面，按三滤波器判据（§1.2 三步判据 + 兄弟前缀消歧）评审新增 `GET /api/v1/monitor/warn-logs`（强类型 DTO，含 level/source/since/limit），并补 `/api/v1/monitor/**` 匿名回归。
3. **多复盘 tag 支持**：`MentalReviewService.REVIEW_TAGS` 白名单扩展（如按业务域分 tag），同步前端 tag 下拉建议值。
4. **后端自动化测试补强**：`WarnLogServiceImpl`（JdbcTemplate mock）与 `MentalReviewService.warnAlerts` 单测纳入 CI，替换手工 curl+psql 验收。
5. **SAFEGUARD 前端联动**：workspace `SAFEGUARD` run 类型已后端就绪，可在 `CognitionPanel` 运行类型选择器追加（本 Phase 未纳入范围）。

