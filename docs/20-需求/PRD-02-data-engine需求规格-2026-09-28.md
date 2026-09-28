# PRD-02 data-engine 需求规格（分册 02）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0
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
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
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
    id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
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
    id             VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
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
    id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
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
    fact_refs_json   JSONB NOT NULL,                -- {stageFactIds:[],resourceFactIds:[],costFactIds:[],attributionIds:[],actionOutcomeIds:[]}
    metric_versions_json  JSONB NOT NULL,           -- {metricCode: version}
    profile_versions_json JSONB NOT NULL,           -- {profileId: version}
    assumption_versions_json JSONB NOT NULL,        -- {assumptionId: version}
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

### 1.4 API（datanet 新增，只增不改）

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/v1/datanet/facts/{factType}/import` | POST | factType ∈ attribution/stage/resource/cost；body=行数组（≤100/批，EN03）；响应 `{batchId, accepted, rejected:[{rowNo, field, ruleId, message}]}` |
| `/api/v1/datanet/facts/{factType}/template` | GET | 下载导入模板（CSV，含表头单位/示例/必填标记） |
| `/api/v1/datanet/facts/{factType}` | GET | 分页查询（RLS 注入后），支持 projectId/period/dqStatus 过滤 |
| `/api/v1/datanet/facts/batches/{batchId}` | GET | 批次状态与错误行下载 |
| `/api/v1/datanet/facts/action-outcomes` | POST | W→D 反馈链写入（PRD-01 §2.2） |

全部端点过三滤波器；写操作发 Kafka `ecos.audit`。

### 1.5 验收标准

1. 样例导入"3 项目 × 2 部门 × 12 月 × 3 环节"（216 行 stage_fact + 归属/资源/成本）全部 PUBLISHED；
2. 坏数据三连拒：重复行（DQ-F01）、attribution_ratio=1.2（DQ-F02）、ALLOCATED 成本无 evidence_ref（DQ-F09）→ rejected 含行号/字段/规则 ID/修复建议；
3. 已 PUBLISHED 行 UPDATE 业务字段被拒（409）；
4. tsc/mvn 绿 + 端点 curl 验收表全过。

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

<!-- PRD-02-data-engine需求规格 / 2026-09-28 / v1.0 -->
