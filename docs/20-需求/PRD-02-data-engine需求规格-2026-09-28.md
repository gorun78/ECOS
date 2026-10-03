# PRD-02 data-engine 需求规格（分册 02）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.1（**v1.1（2026-09-29 批量批准定版）**：凭证 = [需求检视报告 §十四](需求检视报告-2026-09-28.md)。本册按 **Q1 b（MC01 严格 + 扩白名单）** 与报告 **G2-1**（DDL 模板系统性违 MC01/MC02）更正 §1.2 五表草案：`DEFAULT gen_random_uuid()` ×4 → 应用侧生成 UUID（DDL 无默认值），快照表 4 个 `JSONB` 列 → `TEXT`（MC02 受控 JSON 只准 TEXT，`_json` 后缀 DR04 不变）。**另随 R-1b ②**：DQ 归数据引擎，`ecos_dq` 承认并入 `ecos_data`（ST07 修订见数据库访问规范 v1.2）；随 **R-2 a**：`ecos_pipeline_definition` 为唯一可执行模型，YAML 模型转只读归档后迁移删除；随 **R-3 ②+禁静默**：legacy 兜底保留至 `V166` 完成、UI 必须显式标注"旧模型数据"。**"已批准"仅指需求文本生效**：DDL 只落**迁移脚本文件**不实跑库，改业务 Java 代码需逐项再授权（报告 §14.4）；未创建的测试类一律记"未执行"）
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)（DDL/精度/边界全局裁定）
> 覆盖: REQ-DATA-01~08
> 模块: `ecos_backend/engine/data-engine`（datanet:18082）

---

## 一、REQ-DATA-01 业务事实模型扩展（P0）

### 1.1 需求陈述

在 DW 层（CURATED）新增五类业务事实表，支撑场景级经营事实采集（以年度经营预测为首个消费方，模型保持通用命名不绑死场景）。**只加表不删改**（IR03）；表归 datanet 管辖，其他引擎只读。

### 1.2 数据模型（DDL 草案，五表）

> 通用约定：全部含审计 5 字段 + domain + version_no（模板见《数据库访问规范》§四，下文省略重复列）；金额 NUMERIC(18,2)、比率 NUMERIC(5,4)、期间 VARCHAR(7) 'YYYY-MM'（PRD-01 DB-04 裁定）。

**表 1 — 项目经营归属 `ecos_biz_project_attribution`**

```sql
CREATE TABLE IF NOT EXISTS ecos_biz_project_attribution (
    id                VARCHAR(36) PRIMARY KEY,           -- MC01: 应用侧生成 UUID，DDL 禁 gen_random_uuid()
    project_id        VARCHAR(36) NOT NULL,
    contract_id       VARCHAR(36) NOT NULL,
    department_id     VARCHAR(36) NOT NULL,
    attribution_ratio NUMERIC(5,4) NOT NULL,        -- 0~1，DQ 校验
    attribution_type  VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',  -- PRIMARY/PARTNER
    effective_from    VARCHAR(7) NOT NULL,          -- YYYY-MM
    effective_to      VARCHAR(7),                   -- NULL=至今
    source_evidence   VARCHAR(255),                 -- 归属依据（合同条款/审批单号）
    dq_status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/PASSED/REJECTED/PUBLISHED
    -- 审计 5 字段 + version_no + domain（模板省略）
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_bpa_key ON ecos_biz_project_attribution(project_id, contract_id, department_id, effective_from);
CREATE INDEX IF NOT EXISTS idx_bpa_project ON ecos_biz_project_attribution(project_id);
```

约束：同一 (project, contract, period) 下所有归属比例合计 ≤1（DQ-F06 校验，超出→REJECTED）。

**表 2 — 项目环节计划/实际 `ecos_biz_stage_fact`**

```sql
CREATE TABLE IF NOT EXISTS ecos_biz_stage_fact (
    id               VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧生成 UUID，DDL 禁 gen_random_uuid()
    project_id       VARCHAR(36) NOT NULL,
    department_id    VARCHAR(36) NOT NULL,
    period           VARCHAR(7)  NOT NULL,
    stage            VARCHAR(20) NOT NULL,          -- ACCEPTANCE验收/COLLECTION回款/SUBCONTRACT分包
    fact_type        VARCHAR(10) NOT NULL,          -- PLAN/ACTUAL
    contract_base    NUMERIC(18,2) NOT NULL,        -- 合同基数
    attribution_ratio NUMERIC(5,4) NOT NULL,
    realization_rate NUMERIC(5,4),                  -- 实现率 0~1（ACTUAL 可与 amount 二选一，见 1.3-规则3）
    amount           NUMERIC(18,2),                 -- 实现额
    currency         VARCHAR(3) NOT NULL DEFAULT 'CNY',
    source_type      VARCHAR(20) NOT NULL DEFAULT 'MANUAL',  -- MANUAL/IMPORT/PIPELINE
    source_ref       VARCHAR(255),                  -- 来源批次/凭证
    evidence_ref     VARCHAR(255),                  -- 证据引用（附件 docId/URL）
    dq_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- 审计 5 字段 + version_no + domain
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_bsf_key ON ecos_biz_stage_fact(project_id, department_id, period, stage, fact_type);
CREATE INDEX IF NOT EXISTS idx_bsf_period ON ecos_biz_stage_fact(period);
```

**表 3 — 项目资源投入 `ecos_biz_resource_fact`**

```sql
CREATE TABLE IF NOT EXISTS ecos_biz_resource_fact (
    id             VARCHAR(36) PRIMARY KEY,            -- MC01: 应用侧生成 UUID，DDL 禁 gen_random_uuid()
    project_id     VARCHAR(36) NOT NULL,
    department_id  VARCHAR(36) NOT NULL,
    period         VARCHAR(7)  NOT NULL,
    staff_ref      VARCHAR(64),                     -- 人员/岗位标识（脱敏，敏感列走 ST03）
    fte            NUMERIC(5,2),
    work_hours     NUMERIC(8,1),
    hourly_rate    NUMERIC(18,2),
    cost_category  VARCHAR(20) NOT NULL DEFAULT 'SALARY',
    fact_type      VARCHAR(10) NOT NULL,            -- PLAN/ACTUAL
    source_ref     VARCHAR(255),
    dq_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- 审计 5 字段 + version_no + domain
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_brf_key ON ecos_biz_resource_fact(project_id, department_id, period, staff_ref, fact_type);
```

**表 4 — 成本归集/分摊 `ecos_biz_cost_fact`**

```sql
CREATE TABLE IF NOT EXISTS ecos_biz_cost_fact (
    id               VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧生成 UUID，DDL 禁 gen_random_uuid()
    project_id       VARCHAR(36),                   -- NULL=待分摊池
    department_id    VARCHAR(36) NOT NULL,
    period           VARCHAR(7)  NOT NULL,
    cost_category    VARCHAR(20) NOT NULL,          -- SALARY薪酬/MANAGEMENT管理/MARKETING市场
    direct_or_allocated VARCHAR(10) NOT NULL,       -- DIRECT/ALLOCATED
    allocation_rule_ref VARCHAR(36),                -- 分摊规则 ID（ALLOCATED 必填，DQ-F08）
    amount           NUMERIC(18,2) NOT NULL,
    currency         VARCHAR(3) NOT NULL DEFAULT 'CNY',
    fact_type        VARCHAR(10) NOT NULL,          -- PLAN/ACTUAL
    evidence_ref     VARCHAR(255),                  -- ALLOCATED 必填（DQ-F09 无证据成本拒绝）
    dq_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- 审计 5 字段 + version_no + domain
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_bcf_key ON ecos_biz_cost_fact(COALESCE(project_id,'POOL'), department_id, period, cost_category, direct_or_allocated, fact_type);
```

**表 5 — 预测输入快照 `ecos_forecast_input_snapshot`**（写权归预测运行服务，物理落 datanet 管辖，见 PRD-09 FC-02）

```sql
CREATE TABLE IF NOT EXISTS ecos_forecast_input_snapshot (
    snapshot_id      VARCHAR(36) PRIMARY KEY,
    forecast_run_id  VARCHAR(36) NOT NULL,
    as_of_time       TIMESTAMP NOT NULL,
    scope_hash       VARCHAR(64) NOT NULL,          -- sha256(项目集+部门集+期间+情景)
    fact_refs_json   TEXT NOT NULL,                -- MC02: 受控 JSON 只准 TEXT（{stageFactIds:[],resourceFactIds:[],costFactIds:[],attributionIds:[],actionOutcomeIds:[]}）
    metric_versions_json  TEXT NOT NULL,           -- MC02: TEXT（{metricCode: version}）
    profile_versions_json TEXT NOT NULL,           -- MC02: TEXT（{profileId: version}）
    assumption_versions_json TEXT NOT NULL,        -- MC02: TEXT（{assumptionId: version}）
    caliber_id       VARCHAR(36) NOT NULL,
    caliber_version  VARCHAR(20) NOT NULL,
    checksum         VARCHAR(64) NOT NULL,          -- 全内容 sha256，重跑校验
    -- 审计 5 字段 + version_no + domain
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_fis_run ON ecos_forecast_input_snapshot(forecast_run_id);
```

### 1.3 业务规则

1. 事实行一经 `dq_status=PUBLISHED` 即不可 UPDATE 业务字段（更正=新行 + 旧行逻辑删除，保留审计链）；
2. `fact_type=ACTUAL` 且期间已关账（PRD-09 FC-05 period lock）→ 拒绝写入/修改；
3. `realization_rate` 与 `amount` 至少一项非空；两者同时存在时以 `amount` 为准，rate 仅作参考（消费方规则，写入不拦截）；
4. 敏感列（staff_ref、hourly_rate、amount）按 ST03 加密评估：首期 staff_ref 脱敏存储（工号哈希），金额列走 CLS/RLS 控制而非列加密（经营分析需要聚合计算，加密列无法 SUM——**裁定：金额不列加密，以 RLS+CLS+mask 组合防护**，登记为 ST03 例外并附理由）。
   > **【回写 2026-10-03 §7.2-2】**（源自 `详细设计-02` §7.1*D.2 / 设计行 139，R9 只追加）：本条"金额不列加密"单方例外表述**作废**为无条件放行——按《数据库访问规范》v1.2 L182，**任何金额列不列加密须先在 `docs/40-实现/列级加密豁免登记表-2026-09-28.md` 逐列登记 ST03-A 例外（列名 + 载体表 + 不加密理由 + 组合防护），未登记 = 视同 ST03 违规，启动校验 FAIL**。本 PRD 事实五表涉密金额列（`ecos_biz_stage_fact.amount`/`contract_base`、`ecos_biz_cost_fact.amount`、`ecos_biz_resource_fact.hourly_rate`）的逐列登记由 `AmountExemptionRegisteredTest` 护栏校验（护栏已绿），登记内容以该登记表为准。

### 1.4 API（datanet 新增，只增不改）

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/v1/datanet/facts/{factType}/import` | POST | factType ∈ attribution/stage/resource/cost；body=行数组（≤100/批，EN03）；响应 `{batchId, accepted, rejected:[{rowNo, field, ruleId, message}]}` |
| `/api/v1/datanet/facts/{factType}/template` | GET | 下载导入模板（CSV，含表头单位/示例/必填标记） |
| `/api/v1/datanet/facts/{factType}` | GET | 分页查询（RLS 注入后），支持 projectId/period/dqStatus 过滤 |
| `/api/v1/datanet/facts/batches/{batchId}` | GET | 批次状态与错误行下载 |
| `/api/v1/datanet/facts/action-outcomes` | POST | W→D 反馈链写入（PRD-01 §2.2） |

全部端点过三滤波器；写操作发 Kafka `ecos.audit`。
> **【回写 2026-10-03 §7.2-5】**（源自 `详细设计-02` C.2.1，R9 只追加）：`/api/v1/datanet/facts/**` 需登记进 `route-manifest.json`（gateway/BFF/vite/tests 同源消费），并声明其归属 `/api/v1/datanet/**` 收敛前缀之间的关系（与既有 5 套前缀的关系见设计-02 C.2.1 前缀收敛表；本 PRD facts 前缀属 `/api/v1/datanet` 主承流线，不走 `/api/v1/data` 或 legacy `/api/datanet` 线）。

### 1.5 验收标准

1. 样例导入"3 项目 × 2 部门 × 12 月 × 3 环节"（216 行 stage_fact + 归属/资源/成本）全部 PUBLISHED；
2. 坏数据三连拒：重复行（DQ-F01）、attribution_ratio=1.2（DQ-F02）、ALLOCATED 成本无 evidence_ref（DQ-F09）→ rejected 含行号/字段/规则 ID/修复建议；
3. 已 PUBLISHED 行 UPDATE 业务字段被拒（409）；
4. tsc/mvn 绿 + 端点 curl 验收表全过。
> **【回写 2026-10-03 §7.2-6】**（源自 `详细设计-02` §七.1 F02-06 验收，R9 只追加）：本条 §1.5 各行点名的可执行验收标识（禁"人工确认"，对齐设计-02 第二章）：①216 行样例全 PUBLISHED → `FactImportAcceptRejectTest`；②坏数据三连拒（DQ-F01/F02/F09）→ `FactBadDataTripleRejectTest`（rejected 含 rowNo/field/ruleId/suggestion + 一律不入库）；③PUBLISHED 行 UPDATE 拒 409 → `PublishedRowImmutableTest`；④批量 ≤100/EN03 → `FactImportBatchCapTest`（101→拒/100→放行）；⑤活跃唯一（同键第二存活→索引拒，旧行置 NULL 后新行可入）前置 DDL 契约 → `ActiveFlagUniquenessDdlTest`（运行时活库 INSERT 属实跑库授权闸）；⑥金额列 ST03-A 登记 → `AmountExemptionRegisteredTest`。DQ 门禁 11 规则反例 → `DqRuleF01ToF11Test`；禁静默补默认 → `NoSilentDefaultFillArchTest`。

---

## 二、REQ-DATA-02 DQ 发布前门禁（P0）

### 2.1 规则清单（首批，映射六维度）

| 规则 ID | 维度 | 校验逻辑 | 处置 | 消息模板 |
|---|---|---|---|---|
| DQ-F01 | UNIQUENESS | 唯一索引冲突（各表 uniq_*） | REJECT | 第{row}行与已有记录重复（{冲突键}） |
| DQ-F02 | VALIDITY | 比率列 ∈ [0,1] | REJECT | 第{row}行 {field}={value} 超出 0~1 |
| DQ-F03 | VALIDITY | 金额非浮点污染（scale ≤2）、非负（分包允许负属性由口径定义，事实层存原值+标志） | REJECT | 第{row}行金额精度/符号非法 |
| DQ-F04 | VALIDITY | period 格式 YYYY-MM 且在目标年度范围内 | REJECT | 第{row}行期间非法 |
| DQ-F05 | CONSISTENCY | 同批次币种唯一且与口径币种一致 | REJECT | 币种不一致 |
| DQ-F06 | CONSISTENCY | 归属合计：同 (project,contract,period) Σratio ≤1 | REJECT | 归属合计 {sum} 超过 100% |
| DQ-F07 | COMPLETENESS | 必填字段非空（按模板必填标记） | REJECT | 第{row}行缺 {field} |
| DQ-F08 | COMPLETENESS | ALLOCATED 成本必带 allocation_rule_ref | REJECT | 分摊成本缺分摊规则 |
| DQ-F09 | COMPLETENESS | 成本/实际行必带 evidence_ref 或 source_ref | REJECT | 无证据成本拒收 |
| DQ-F10 | ACCURACY | contract_base 与该合同当前生效版本基数一致（主数据比对） | WARN→待处理队列 | 合同基数与主数据不一致 |
| DQ-F11 | FRESHNESS | ACTUAL 行期间 ≤ 当前月 | REJECT | 未来期间实际值非法 |

### 2.2 门禁流程

```
import → 行级校验(DQ-F01~F11) → rejected 直接返回（不入库）
       → accepted 行落表 dq_status=PASSED → 批次完整性检查（覆盖率阈值，如目标期间缺口>10% 提示）
       → 人工/自动 publish → dq_status=PUBLISHED（仅 PUBLISHED 行可被预测快照引用）
```

**红线**：异常必须进待处理队列并可点击定位（前端联动 PRD-08 FE-03）；**禁止静默缺省值覆盖**（不得为通过校验补 0/补默认）。

### 2.3 验收标准

1. 11 条规则各有 1 正 1 反用例（单测）；
2. 故意提交三类坏数据全部拦截且 rejected 信息含行号/字段/规则 ID/修复建议；
3. 预测快照仅能引用 PUBLISHED 行（集成断言）。

---

## 二·补、REQ-DATA-09 DQ 治理单模型收敛与 404 掩蔽禁令（P0，本册新增）

> **【回写 2026-10-03 §7.2-4】**（源自 `详细设计-02` §7.2 项 4 / §1.1 D-5/D-6，R9 只追加，本条为新立需求）：本 PRD 原 §二 REQ-DATA-02 仅覆盖"发布前门禁 11 规则"，**缺"DQ 双模型收敛 + 错误掩蔽"独立需求**，导致 F02-08/DATA-02 治理侧验收不可判定。新立 REQ-DATA-09：
1. **治理单模型收敛**：DQ 治理以单模型承载（`dq_rule` 定义态 + `dq_rule_check` 运行态），legacy `public.ecos_dq_rule`/`ecos_dq_issue` 读端点保留 ≥2 迭代 + `deprecated=true`，写端点停用（转 410）；
2. **404 掩蔽禁令**：数据层 SQL/映射异常**不得**被掩蔽为 404，改 `500 + ECOS-DATA-031`（附 traceId）；仅真 `NoHandlerFound/NoResourceFound` 才 404（`ECOS-DQ-111` 治理记录不存在的真实 404 与 031 严格区分）；
3. **前端禁回落 legacy**：`pages/data-quality/api.ts` 删除"404 回落 legacy"分支，改为显式错误提示（R-3 ②+禁静默：兜底保留至 V166 完成，期间 UI 必须显式标注"旧模型数据"）。

**验收**：`NoFourOhFourMaskingTest`、`WorkOrderListSqlContractTest`、`DqGovernanceEndpointReachabilityTest`、`DqLegacyMigrationParityTest`（详见设计-02 F02-08 验收标识）。

---

## 三、REQ-DATA-03 批量导入与模板（P0）

**三份模板列定义**（CSV，UTF-8，首行表头含单位，第二行示例，第三行起数据）：

| 模板 | 列（* = 必填） |
|---|---|
| 环节计划/实际 | project_id* · department_id* · period*(YYYY-MM) · stage*(ACCEPTANCE/COLLECTION/SUBCONTRACT) · fact_type*(PLAN/ACTUAL) · contract_base* · attribution_ratio* · realization_rate · amount · currency* · evidence_ref |
| 资源投入 | project_id* · department_id* · period* · staff_ref · fte · work_hours · hourly_rate · cost_category* · fact_type* |
| 成本分摊 | project_id · department_id* · period* · cost_category*(SALARY/MANAGEMENT/MARKETING) · direct_or_allocated*(DIRECT/ALLOCATED) · allocation_rule_ref(ALLOCATED必填) · amount* · currency* · evidence_ref(必填) |

**交互要求**：导入后逐行错误可在线修复（编辑重提交该行）；错误行可下载（原行 + 追加 error_rule/error_message 两列）；再次校验幂等（同批次重传按 DQ-F01 去重提示而非重复入库）。

**验收**：错误行下载文件含错误原因列；修复重传后 accepted。

---

## 四、REQ-DATA-04 DQ 告警接入 runtime-monitor（P1）

**细化**：
1. `buildContext()` 空转修复：评估上下文真实取数（样本行/统计值来源明确），Phase 2 口径维持只读 `dq_rule_check` 不拉样本的既有裁定不变——修复点是 context 组装逻辑接入真实 check 结果，而非新增采样；
2. 告警分级状态机：`P3(24h) / P2(1h) / P1(15min) / P0(5min)` 响应时限；`P2 5min 未 ack → P1`、`P1 15min 未 ack → P0`（既有规范，接线到 runtime-monitor 告警通道，禁 data-engine 自建监控）；
3. 告警载荷含：ruleId、对象（表/批次/数据源）、severity、traceId、修复入口链接。

**验收**：触发一条 DQ 告警 → runtime-monitor 收到 → 未 ack 按时限升级 → 升级事件可查。

---

## 五、REQ-DATA-05 DriverManager 直连清偿（P1，= PMO-73 G1-T1）

**违规点清单（2026-09-28 grep 实证）**：

| # | 位置 | 语义 | 替换方案 |
|:--:|---|---|---|
| 1 | `QueryExecutionServiceImpl:69` | SQL 控制台执行 | 注入 `JdbcConnector.executeQuery` |
| 2 | `QueryExecutionServiceImpl:141` | 同上（另一分支） | 同上 |
| 3 | `DataDescriptionDiscoveryImpl:64` | 元数据探查 | `JdbcConnector` 元数据 API |
| 4 | `MetadataRowCountService:117` | 行计数（EXACT 模式） | `JdbcConnector.executeQuery` |
| 5 | `BaseJdbcAdapter:99` | 数据源连接测试/适配 | **裁定项**：连接测试语义特殊（测的是目标源连通性而非业务查询）。裁定=仍改为经 runtime-access 提供的 `testConnection(url,props)` 能力；若 runtime-access 暂无该方法则先补充（§2.5-6 补强而非自建），不豁免 |

**合法点**：runtime-access `JdbcConnector` 内 5 处（收敛点本体）。
**验收**：改造后 grep 业务代码 0 命中；ARCH-06 规则上线；PG + Doris（如环境可用）双源 SQL 控制台回归 curl 200；连接测试功能回归。

---

## 六、REQ-DATA-06 Git 归档 path-traversal 校验（P1，= PMO-73 G1-T2）

**规格**：`MetadataCollectGitArchive` 及全部 `repoRoot` 消费点（含 `MetadataCollectTaskExecutor`、datanet `GitController`）：

```java
Path root = Paths.get(repoRoot).toAbsolutePath().normalize();
Path target = root.resolve(datasourceId).normalize();
if (!target.startsWith(root)) { throw new ValidationException("illegal archive path"); }
```

1. repoRoot 来自 sys_config（不可信输入）：加载时即校验为绝对路径 + normalize 后存在性；
2. datasourceId 虽为 DB UUID，仍执行 startsWith 双保险；
3. 拒绝时 warn 日志（含 traceId）+ 业务异常，不裸 500。

**验收用例**：`repoRoot=/data/repos`、`datasourceId=../../etc` → 拒绝；正常 UUID → 通过；单测覆盖 symlink 逃逸（toRealPath 可选加固）。
> **【回写 2026-10-03 §7.2-3】**（源自 `详细设计-02` D-22，R9 只追加）：REQ-DATA-06 状态从"待实现"更正为"—**已实现，残余三处**："。本条路径穿越校验主体**已闭合**：`GitRepoRootResolver` 已具 `requireSafeRoot`（绝对路径 + 禁 `..` + normalize）/ `requireSafeSegment`（禁 `..`/`/`/`\`/`:`）/ `resolveUnderRoot`（逐段校验 + `startsWith(base)`）/ `requireInsideRoot`，`MetadataCollectGitArchive:81` 对 `datasourceId` 已调 `requireSafeSegment`（设计-02 D-22 实测）。**残余**（转 F02-12 加固项，本 PRD 需求语义不变、仅补覆盖）：① repoRoot **存在性校验**（`Files.exists/isDirectory/writable`，加载时）② `toRealPath` **symlink 解析**后再 `startsWith` ③ datanet **`GitController`** 与 pipeline 侧**三处调用方统一复用 resolver**（现仅归档侧已接入）。验收标识见 `详细设计-02` F02-12（`RepoRootSymlinkEscapeTest` / `RepoRootMissingDirectoryTest` / `AllGitCallersUseResolverArchTest`）。

---

## 七、REQ-DATA-07 RAW 层 parquet writer（P2）

**裁定**：不引入 parquet-mr 新依赖（基线红线），复用 runtime-access 已收敛的 **DuckDB** 做 csv→parquet 转换写出。

**规格**：
1. `SINK_MINIO` 节点配置 `format: csv|parquet`；显式 `parquet` → DuckDB `COPY (...) TO 'file.parquet' (FORMAT PARQUET)` 经临时文件上传 MinIO；未显式指定 → 维持 csv 兼容 + warn（既有行为）；显式其他值（如 avro）→ 明确拒绝（既有行为）；
2. key 规范不变：`raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{ts}.parquet`；
3. `dw.lake.storage_format=parquet` 时管道默认走 parquet。

**验收**：parquet 对象可被 DuckDB 读回且行数/列型一致；key 合规。

---

## 八、REQ-DATA-08 A3 非结构化过渡态退出（P1）

**迁移步骤**：
1. 前置确认：`SOURCE_MINIO` + `TRANSFORM_DOC_PARSE` → `ecos_dw.doc/doc_chunk` 链路已生产可用（数据湖规范 §七已标闭合）；
2. kb-engine 侧 `kb_doc_chunk` 写入路径移除：`KnowledgeDocIngestService.ingest` 第 6~7 步（切分/向量化落 kb 表）改为消费 datanet DW 层 doc_chunk（REST 读），kb 仅保留向量索引写入（`KnowledgeVectorWriteService`，属 K 层自有资产不迁）；
3. 双跑对比期 1 个批次：新旧路径 chunk 数量/内容 hash 对比，diff=0 后切断旧路径；
4. 回滚开关：`ecos.kb.ingest.legacy_chunk_write=true`（过渡期保留，收口后删除配置）。

**验收**：非结构化 7 步链路 E2E 回归（上传→MinIO→解析→DW doc_chunk→kb 向量化→检索命中）；kb-engine grep 无 `kb_doc_chunk` INSERT。

---

## 九、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| DATA-01 | PRD-01 DB-04 精度裁定、PRD-09 FC-06 主数据 | FC-02 快照、KB-02 画像取数 | 场景批次 A（延后，但 DDL 评审可先行） |
| DATA-02 | DATA-01 | FE-03 待办化 | 场景批次 A |
| DATA-03 | DATA-02 | — | 场景批次 A |
| DATA-04 | runtime-monitor 可用 | — | PMO-73 G5 |
| DATA-05 | runtime-access JdbcConnector | ARCH-06 | **PMO-73 G1** |
| DATA-06 | — | — | **PMO-73 G1** |
| DATA-07 | runtime-access DuckDB | — | PMO-73 G5 |
| DATA-08 | TRANSFORM_DOC_PARSE 生产可用 | — | PMO-73 G5 |
| DATA-09（2026-10-03 §7.2-4 新增） | DATA-02 / ADR-15 | 09 册 DQ 门禁验收 | 场景批次 A（P0） |

> **【回写 2026-10-03 §7.2-8】**（源自 `详细设计-02` C.1 / §7.2 项 8，R9 只追加）：本 PRD 涉及 datanet 承流的表述须与 **ADR-15 双口径一致化**——S0 现状 = 37 个 data-engine Controller 全由 gateway :8080 单体宿主（`@ComponentScan engine.data.*`）；S2/S3 目标态 = 路由切流至 datanet :18082 独立承流，但其切流前置（信任链 HeaderAuthInterceptor + 端点差集清零 + 匿名/准入清单登记）未满足前，datanet **不得**独立对外承流（设计-02 C.1 S2/S3 前置门禁 + `DatanetEndpointParityTest` 验收）。本 PRD 所有 `/api/v1/datanet/**` 端点在承流态切换**前后表现一致**（非 404 且响应体结构等价），不随承流切换改变签名（铁律 #9 API 只增不改）。

<!-- PRD-02-data-engine需求规格 / 2026-09-28 / v1.1（2026-09-29 随需求检视报告 §十四 批量批准定版） -->
<!-- 【回写 2026-10-03】依 `详细设计-02` §7.2「PRD 侧需回写清单」落六项：项2 §1.3-4 金额列 ST03-A 逐列登记（AmountExemptionRegisteredTest 护栏）、项4 新立 §二·补 REQ-DATA-09 DQ 治理单模型收敛 + 404 掩蔽禁令、项5 §1.4 route-manifest 登记声明、项6 §1.5 验收点名可执行 spec 标识、项3 §六 REQ-DATA-06 更正为"已实现 + 残余三处"、项8 datanet 承流 ADR-15 双口径一致化 + §九 DATA-09 追溯行。项1（§1.2 DDL 草案作废，v1.1 已按 G2-1/MC01-MC02 更正 gen_random_uuid×4→应用侧 UUID、JSONB→TEXT）与项7（PRD-07 §3.2 authHeaders，见 PRD-07）不在本文件。R9 只追加、不改既有行。 -->
