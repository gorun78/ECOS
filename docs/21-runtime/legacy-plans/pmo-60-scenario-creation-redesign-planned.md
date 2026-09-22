# PMO-60 — 重新规划：场景创建流程（认知引擎协同期）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣 | 日期: 2026-09-21 | 优先级: P0
> 工作流: L3 裁量流（P0.1 契约 → P0.2 心智模型设计 → P1 数据/契约 freeze → P2 实施 → P3 验收 → P4 发布）
> 全链路: 前端 4 文件 + 后端 3 模块（workspace/cognitive 对齐/sysman·安全·接口表）向上贯通

## §背景

**现状**：「创建场景」是一个 7 步线性向导（[ScenarioEditor.tsx](../ecos_frontend/src/pages/scenario/ScenarioEditor.tsx)），分别勾选 6 类**硬编码 mock** 资源（[data.ts](../ecos_frontend/src/pages/project-workbench/data.ts) AVAILABLE_DATASETS/OBJECTS/KNOWLEDGE/AGENTS/INTERFACES/SECURITY）。保存后写入 `ecos_scenario_binding`，但 `target_ref` 是**字符串别名**（如 `ds_flight_schedules`），**不指向任何 PG 真主键**。运行时 `CognitionPanel` 才临时调认知引擎做诊断/预测/模拟。

**矛盾**：
1. **引用关系空壳**：`target_ref` 全是 string 标签，认知引擎做「反驳式检验」时找不到真知识库/真 Agent/真数据源
2. **心智层脱钩**：cognitive-engine 已有 3 表落盘能力（`ecos_cognitive_evidence`/`ecos_cognitive_hypothesis`/`ecos_cognitive_belief`，PMO-59 P2a，ADR-9）但**创建场景时零关联**——场景要等"运行"才碰认知
3. **选择范围割裂**：`AVAILABLE_KNOWLEDGE` 等是前端写死的 mock，用户看不到**真实**知识库清单
4. **安全/接口资源未落地**：6 类中 2 类（SECURITY_POLICY / INTERFACE）**无真表**，无法真绑

**目标**：将「创建场景」从「**绑 6 类资源 string 标签**」升级为「**绑 6 类真资源 ID + 给场景装一盏心智灯**」，与认知引擎形成「场景=一个可携带认知心智状态的智能决策单元」。

## §禁止清单

1. ❌ 不复用 `// @ts-nocheck`（前端五铁律 §4.6）
2. ❌ 不新增 Docker 容器、不新增 Maven 模块（架构铁律 §0.4 不改基线）
3. ❌ 不动 11 张 `ecos_demo.dw_*` 演示表（历史保留区）
4. ❌ 动 `ecos_scenario_binding.target_ref` 类型（**只增不改**：加新列，老列保留作兼容）
5. ❌ 不引入 Drools 等传统规则引擎（cognitive 仅 SpEL，PMO-20）
6. ❌ 新增 KB 写入端点（只复用 `GET /api/v1/knowledge/assets` 列表）
7. ❌ 不动 security-engine ABAC 评估端点（§2.4-4）
8. ❌ frontend 改 `KnowledgeComplianceCheckTab`/`KnowledgeView` 等**上一文件补过的 i18n**（这是已完成的交付物，不动）

## §P0.1 契约侦察 — 四类资源真 ID 基线

| # | 资源类 | 真主键列 | 真 CRUD 端点 | 跨服务坐落 | P1 绑真 ID |
|:--:|---|---|---|---|:---|
| 1 | **数据源** | `td_datasource.datasource_id VARCHAR(64)` | `GET /api/v1/datanet/datasource` | datanet :18082 | ✅ 直绑 |
| 2 | **本体实体** | `ecos_ontology_entity.id VARCHAR(64)`（`code` 业务键） | `GET /api/v1/ecos/ontologies/{oid}/entities` | buszhi :18083 | ✅ 下拉域名 → 实体 |
| 3 | **知识资产** | `ecos_knowledge.knowledge_article.id VARCHAR(64)` | `GET /api/v1/knowledge/assets?status=active` | dccheng :18086 | ✅ 直绑（article 粒度） |
| 4 | **AI 智能体** | `sys_agent_profile.id VARCHAR(64)` | `GET /api/v1/agent/profiles` | aiming :18084 | ✅ 直绑 + `/agent/tools` 清单 |
| 5 | **安全策略** | **无真表**（仅 `sys_dict` 兜底） | `GET /api/v1/security/policy-engine/evaluate`（**仅评估端点**） | sysman :18081 | ⚠️ 需新建 `ecos_security_policy` |
| 6 | **接口** | **无真表**（纯字符串标签） | 无 | — | ⚠️ 需新建 `ecos_interface_ref` |

**前端选项池**：当前 6 套 mock 在 `data.ts`（26 字符串别名），P1 全替换为后端联查。

> 详见 git 历史 commit `146cd1d` 之前的 P0.1 探索报告；本报告以现状 P0.2 决策为准。

## §P0.2 心智层数据模型 + 流程设计

### 2.1 认知心智层概念模型（§6 前置补充）

> **心智三要素（落盘，ADR-9 三表）**
> - `ecos_cognitive_evidence` 证据（可来源溯源 / 可信度 / 冲突标记）
> - `ecos_cognitive_hypothesis` 假设（受证据支撑 / 可失效）
> - `ecos_cognitive_belief` 不确定性判断（`variable` + 离散 `prob[]` 且 `sum(prob)=1` 强校验）
>
> **认知推出层（计算时间点，ADR-9 不落盘）** 四件套端点：
> | 端点 | 业务语义 | 回答 |
> |:--|---|---|
> | `POST /api/v1/cognitive/diagnose` | 诊断（因果推理） | 根因是什么 / 为什么 |
> | `POST /api/v1/cognitive/forecast` | 预测（模型资产 PMO-51） | 接下来会怎么变 / 偏差多少 |
> | `POST /api/v1/cognitive/simulate` | 推演（反事实） | 如果 X → Y，会连锁 Z |
> | `POST /api/v1/cognitive/policy` | 策略（处置方案） | 因此该做什么 / 风险与合规检查 |
>
> **认知底座** `ecos_cognitive_model`（PMO-51，独立于三表；由场景 `mind.model_refs[]` 多选挂载）
>
> **决策层**（业务层，可选落盘）`DecisionService` 消费四件套输出 → `action_plan[]`
>
> **★ 策略（policy）不是三要素**：`/policy` 是四件套的**输出端点**，其产物（`action_plan[]`）属「决策回执」，**不进入** `ecos_scenario_mind`，也**不属于** `evidence/hypothesis/belief`。三要素具备「可被证据否定 / 可版本覆盖」的内禀属性；策略是「基于此刻三要素 + 三件套输出的应然判断」，证据一变即须重算，故只作为瞬态输出，采纳后才可选落 `ecos_decision_record`。
>
> **★ 认知心智层 vs 知识库（解耦）**：`ecos_cognitive_evidence` 的 `source_type` 是多态枚举，**KB 只是「来源之一」**，不直接成为心智要素：
> | source_type | source_ref 指向 | 说明 |
> |:--|---|---|
> | `PROD_DATA` | `td_datasource.datasource_id` | 生产数据流实例化 |
> | `KB_ARTICLE` | `knowledge_article.id` | 知识库条文被调阅后实例化为一条证据 |
> | `AGENT_RESULT` | `agent 运行 run_id`（瞬态） | Agent 产出 |
> | `USER_INPUT` / `REPORT` / `API` / `SENSOR` | 各自来源 | 其余来源 |
> 规则：KB 条文**只被引用**（`source_ref`），内容不复制进 mind；运行时 cognitive 按需 `GET /api/v1/knowledge/articles/{id}` **只读**拉取；KB 修订 → 引用它的 evidence 标记「可能失效」。同一条 KB 可被多个场景的多条 evidence 引用，**KB 与心智松耦合**。

### 2.2 心智层数据模型（V130__ecos_scenario_mind.sql）— **多心智变体，独立于场景定义**

> **架构决策 (ADR-PMO60-1)**：心智管理**独立于**场景定义 —— 心智是 cognitive 域自治资产，场景是「业务引用者」。通过 **1:N 物理表 + 独立 REST 子资源 + 激活哨兵** 实现，**不**额外建桥表（避免过度工程化，见 §2.10 设计判断）。

```sql
CREATE TABLE IF NOT EXISTS ecos_scenario_mind (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id           VARCHAR(64) NOT NULL,                 -- ← 1:N（不再 UNIQUE）
    mind_label            VARCHAR(64) NOT NULL DEFAULT 'base',  -- 变体标签：base / optimistic / conservative ...
    active_mind           SMALLINT NOT NULL DEFAULT 0,          -- 激活哨兵（0/1），每 scenario 至多 1 行 =1
    initial_belief_jsonb  JSONB DEFAULT '{}'::jsonb,   -- 不确定性判断初始化（{variable, prob[]}）
    evidence_refs         JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_evidence.id 数组
    hypothesis_refs       JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_hypothesis.id 数组
    model_refs            JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_model.id 数组（PMO-51 预测底座）
    cognitive_endpoints   JSONB DEFAULT '{}'::jsonb,   -- 四件套端点启停 + 权重
    -- cognitive_endpoints 期望 schema（每心智变体可独立配置权重）：
    -- {
    --   "diagnose":   { "enabled": true,  "weight": 1.0, "causal_depth": 3 },
    --   "forecast":   { "enabled": true,  "weight": 0.8, "model_id": "mos_xxx" },
    --   "simulate":   { "enabled": false, "weight": 0.5, "counterfactors_max": 10 },
    --   "policy":     { "enabled": true,  "weight": 1.0, "precedent_enabled": true }
    -- }
    initial_confidence    DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    create_time           TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time           TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by             VARCHAR(64) DEFAULT 'system',
    update_by             VARCHAR(64) DEFAULT 'system',
    is_deleted            SMALLINT NOT NULL DEFAULT 0
);
-- 1:N 索引
CREATE INDEX IF NOT EXISTS idx_mind_scenario ON ecos_scenario_mind (scenario_id, is_deleted);
-- 同一 scenario 内 mind_label 唯一（区分变体）
CREATE UNIQUE INDEX IF NOT EXISTS uq_mind_scenario_label
    ON ecos_scenario_mind (scenario_id, mind_label)
    WHERE is_deleted = 0;
-- 激活哨兵：每 scenario 最多 1 行 active_mind=1（partial index 兜底）
CREATE UNIQUE INDEX IF NOT EXISTS uq_mind_active
    ON ecos_scenario_mind (scenario_id)
    WHERE is_deleted = 0 AND active_mind = 1;
```

> **设计要点**：
> - **1:N** 心智：同场景多 mind 并存（`base`/`optimistic`/`conservative`/…），**至少 1 行** `active_mind=1`
> - **独立 REST 子资源**：`/scenarios/{sid}/minds`（复数）独立 CRUD，与场景 CRUD 解耦（场景重命名不改 mind；mind 可单删不动 scenario）
> - **激活语义**：未指定 `mind_id` 的 cognitive 端点调用 → 走 `active_mind=1` 那道；调用方传 `?mind={mind_id}` 走指定变体
> - **JSONB 四件套形态不变**：只是心智行从 1 行变 N 行，每个 mind 自带自己的 evidence_refs/hypothesis_refs/model_refs/belief
> - **cognitive 引擎侧**完全无感：只认 `mind_id` 入参，不知道场景语义如何
> - **弱点披露**：某 mind 被删但 evidence/hypothesis/belief 已被引用入 `ecos_decision_record.source_refs[]` 时，决策回查保留 hash，不依赖 mind_id 存在（**hash 留痕，不强 FK**）

> **为什么不加桥表**（与否：选 B 不选 C）：
> - Q4 场景需要的是「同一项目多档心智」——1:N 同一 scenario 多行即达成，不涉及「一 mind 跨多场景」反向多对多
> - 反向多对多（同一套"化工景气心智"复用给 3 个场景）是**未来需求**，若真出现再上桥表 `ecos_mind_ref(scenario_id, mind_id)`，届时改 DDL 成本可控（本节设计不让这一需求**阻塞**当前落地）
> - 应用层并发签「激活哨兵」靠 partial unique index 兜底，避免分布式锁

### 2.3 语义模型：场景是「窗」，心智是「房」，推演是「活动」，外部源是「光」

> 回答「认知与场景的关系（Q3）」防反模式：**场景不生成认知**，认知生成于场景外（证据从 PD/KB/Agent 流入），场景只是**聚合 + 唱例 + 授权 + 审计挂钩**。

| 层 | 关系 | 含义 |
|:--|---|---|
| **结构** | 场景 1:N 心智（至少 1 行 active） | 创建场景自动生成 1 个「base」mind；分析师可加 `optimistic`/`conservative` 等变体；激活哨兵保证 ≤1 行 active |
| **数据** | 场景 1:N binding（6 资源）；场景 1:N mind | binding=资源清单，mind=认知清单（多档），**二者并列独立** |
| **运行时** | mind 是四件套端点的 `inline_context` 入口 | `POST /scenarios/{id}/cognitive/*?mind={mind_id}`（缺省 active）→ workspace 读 mind 三要素 → 透传 cognitive:18089 → 瞬态返回 |
| **授权** | 场景 security binding 限定四件套可读认知范围 | security-engine ABAC 评估 `scene_id + action=diagnose` 决定能否看哪些 evidence |

> 「窗」的语义：场景不带认知时是**空窗**（mind 已建但 evidence/hypothesis_refs 空、belief 是 passive 先验概率）；证据流进来后心智才被「点亮」。

#### Q4 企业决策场景实例（`proj_cz26_chem_invest`：5 亿化工新增投资决策）

```
【数据底座 · 外部】
  PROD_DATA  ds_chem_group_monthly   化工月产值/库存周转
  PROD_DATA  ds_hist_invest          3 个已投案例 ROI
  KB_ARTICLE kb_caqc_capa_2024       《产能预警标准》
  MODEL      mos_chemical_2026       景气度预测模型 (回测 MAE=0.12)

【心智三要素 · 场景 sc_cz26_chem_invest 的 mind】
  evidence: ev_001(产值环比18%) ev_002(KB产能过载) ev_003(历史ROI<8%)
            ev_004(董事长意向) ev_005(原料价-5.1%)
  hypothesis: hy_001「投5亿回报<8%」(支撑:ev_002,ev_003)
             hy_002「不投→24m转型失败」(支撑:ev_004,ev_001)
  belief: variable=invest_decision
          states=[approved,rejected,deferred]  prob=[0.10,0.15,0.75]→[0.20,0.25,0.55]
          version 1→2（forecast 后重估）

【四件套端点 · 运行态触发（瞬态不落盘）】
  /diagnose → 根因「行业产能过载」(hy_001, depth3, conf 0.72)
  /forecast  → 12/24/36m 现金流 p10/50/90 + MAE 0.12
  /simulate  → counterfactor{hy_001→rejected} → 24m 竞对占产能+12%, 集团ROI 9.5%→7.1%
  /policy    → action_plan「投3亿 instead of 5亿」+ 合规passed + risk low

【回执 · 可选落盘】
  ecos_decision_record: action_plan JSONB + source_refs[](指 4 件套输出 hash)
```

> 决策路径四级（左→右），**策略是唯一「写得起约束」的一级**（落 `ecos_decision_record`），前三级始终瞬态：
> `证据/假设/信念 (心智·落盘) → 诊断/预测/推演 (瞬态) → 策略 (瞬态→可选落决策回执)`

### 2.4 升级 binding schema（V131）

```sql
ALTER TABLE ecos_scenario_binding 
  ADD COLUMN IF NOT EXISTS target_id   VARCHAR(64),
  ADD COLUMN IF NOT EXISTS target_type VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_scenario_bind_tid ON ecos_scenario_binding (target_id);
```

兼容策略：旧 binding `target_ref` 保留只读；新 binding 优先填 `target_id + target_type`，`target_ref` 仍写在 ID 字符串（保持兼容，老客户端可读）。

### 2.5 新建 2 张真资源表（P1 sysman，sys_man 库）

**`ecos_security_policy`**（V132）：
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(64) PK | `sp_xxxxxxxx`（避免与 IAM 既有 `sp` 序号撞车） |
| `name` | VARCHAR(255) UNIQUE | 可读名 |
| `domain` | VARCHAR(128) | 适用域（数据/知识/Agent/接口） |
| `policy_expr` | TEXT | ABAC 表达式（OPA/SpEL 任一合法表达式） |
| `priority` | INT DEFAULT 100 | 评估顺序 |
| 审计六列 | — | id/create_time/update_time/create_by/update_by/is_deleted |

**`ecos_interface_ref`**（V133）：
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(64) PK | `ifc_xxxxxxxx` |
| `name` | VARCHAR(255) UNIQUE | 可读名 |
| `interface_type` | VARCHAR(32) | HTTP/KAFKA/REST/MQ/AMQP |
| `endpoint` | VARCHAR(512) | 调用点（host:port/path / topic） |
| `method` | VARCHAR(16) | HTTP 方法 |
| `timeout_ms` | INT DEFAULT 3000 | |
| 审计六列 | — | |

> sysman 模块已有 `sysman-boot` Flyway 目录（V124/V126/V134...），**追加 V132/V133 不破坏既有 migration 序号**。

### 2.6 流程设计（新 7 步 → 新 6 步，心智层并行）

```
┌────────────────────────────────────────────────────────────┐
│ 1. 场景元素（name/goal/desc/dept/priority/budget/safetyIdx）│
├────────────────────────────────────────────────────────────┤
│ 2. 数据·本体·知识·Agent （4 类并行·后端联查选项池）          │  ◄── 资源四联查
├──────────────┬───────────────────────────────────────────┤
│ 3. 安全·接口  │ 4. **心智灯·认知引擎**（NEW：初始假设/证据/
│ （P1 升真）   │   不确定性判断 + 关联认知模型 + 四件套开关 + 启 base mind；
│               │   后续分析师可加 optimistic/conservative 变体）
├──────────────┴───────────────────────────────────────────┤
│ 5. **预校验闸**（NEW：跨服务 resolve 真 ID 可达 + 重表校验 +
│   security 审计 + AI 卡链路规约 + active_mind 唯一性校验）
└────────────────────────────────────────────────────────────┘
        ↓ 通过
   坐库 ecos_business_scenario + 至少 1 行 mind (base) + binding
```

**关键变化**：
- 第 1 步：纯文本，不变
- 第 2 步：**四类资源合并 1 步**，左中右三栏并列（数据源/本体实体/知识资产 + 下拉），智能体单独右侧栏，**界面简化**
- 第 3 步：安全策略 + 接口（P1 升真后真 ID；现 mock 与真表 ID **双轨**，适配平滑）
- 第 4 步：**NEW** — 心智档（含四件套开关）
  - 初始假设（3 条制，可加）→ 自动落 `ecos_cognitive_hypothesis`
  - 初始证据（可多源，可信度 0~1）→ `ecos_cognitive_evidence`
  - 不确定性判断（变量 + 离散概率分布 prob 和=1 **强校验**）→ `ecos_cognitive_belief`
  - 关联认知模型（`ecos_cognitive_model` id 多选）→ 写入 `ecos_scenario_mind.model_refs`
  - **认知四件套启停 + 权重**（写入 `cognitive_endpoints` JSONB）：
    - `diagnose` 诊断（因果深度 N）— 默认 `enabled=true / weight=1.0`
    - `forecast` 预测（指定 model_id + 权重）— 默认 `enabled=true / weight=0.8`
    - `simulate` 推演（反事实上限）— 默认 `enabled=false / weight=0.5`
    - `policy` 策略（是否启用先例参考）— 默认 `enabled=true / weight=1.0`
- 第 5 步：**NEW** — 一键预校验
  - 4 类真 ID resolve（DATASOURCE/ONTOLOGY_ENTITY/KNOWLEDGE_ARTICLE/AGENT）
  - 2 类真 ID（SECURITY_POLICY/INTERFACE）经 `sysman`
  - 联动 `GET /api/v1/agent/tools` 校验 Agent 能力闭环
  - security 链路预演（mock 1 条代表数据触发 security-engine 评估，**不落库**）
  - 心智档 `belief` 概率和=1 强校验 + 引用表存在性
  - **四件套端点跨服务可达性**（对每键 `enabled=true` 的认知端点发 `GET /api/v1/engine/cognitive/health`，失败即 WARN 而非 FAIL）
  - **不通过项标记红字，允许保存为 DRAFT**（不阻塞创建，仅"READY"才允许 ACTIVE）
- 第 6 步：保存（替换原第 7 步"安全阻断"——独立拆出来更安全治理）

### 2.7 新增端点（workspace-scene :18090，P1）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/workspace/scenarios/available/datasets` | 调 datanet `:18082` |
| GET | `/api/v1/workspace/scenarios/available/objects?domain=xxx` | 调 buszhi `:18083`（下拉域→实体） |
| GET | `/api/v1/workspace/scenarios/available/knowledge?domain=xxx` | 调 dccheng `:18086` 列表 |
| GET | `/api/v1/workspace/scenarios/available/agents` | 调 aiming `:18084` |
| GET | `/api/v1/workspace/scenarios/available/security` | 调 sysman `:18081`（P1 新表） |
| GET | `/api/v1/workspace/scenarios/available/interfaces` | 调 sysman `:18081`（P1 新表） |
| POST | `/api/v1/workspace/scenarios/{id}/mind-model` | 落 `ecos_scenario_mind`（**兼容保留**，实际建 base mind） |
| GET | `/api/v1/workspace/scenarios/{id}/mind-model` | 读 base mind（**兼容保留**） |
| DELETE | `/api/v1/workspace/scenarios/{id}/mind-model` | 删 base mind（**兼容保留**） |
| **GET** | `/api/v1/workspace/scenarios/{id}/minds` | **P3b 新增**：列出场景全部心智变体（每行 mind_label/active/四件套状态） |
| **POST** | `/api/v1/workspace/scenarios/{id}/minds` | **P3b 新增**：加一个变体 mind（label/三要素/四件套开关）；`label=base` 首条默认 active=1 |
| **PATCH** | `/api/v1/workspace/scenarios/{id}/minds/{mindId}` | **P3b 新增**：改某 mind 的三要素/四件套；改 `active_mind` 时本 scenario 其它变体 active 强制归 0 |
| **DELETE** | `/api/v1/workspace/scenarios/{id}/minds/{mindId}` | **P3b 新增**：删某变体；删 base 时其它变体自动补 active=1 |
| POST | `/api/v1/workspace/scenarios/{id}/pre-validate` | 一键预校验（返回逐项 PASS/FAIL + **active_mind 唯一性**） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/diagnose?mind={mindId?}` | **P3b 新增**：场景级诊断（缺省走 active mind；显式 mindId 可指定变体） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/forecast?mind={mindId?}` | **P3b 新增**：场景级预测（某 mind 指定的 model_id 生效） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/simulate?mind={mindId?}` | **P3b 新增**：场景级推演（可传 `counterfactors[]`；支持「两 mind 间对比推演」`?mindA&mindB`） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/policy?mind={mindId?}` | **P3b 新增**：场景级策略建议（四件套汇总 → `action_plan[]` + 风险） |

> 端点铁律：路径用 `/api/v1/` 前缀 + `VersionPrefixRewriteFilter` 的 `V1_REWRITE_MAP` + `SecurityConfig.permitAll` + `ClearanceInterceptor` 豁免 三滤波器逐层加（架构铁律 §1.2）。

### 2.8 前端文件改动清单（P2）

| 文件 | 操作 |
|---|---|
| `ecos_frontend/src/pages/scenario/ScenarioEditor.tsx` | 7 步→6 步向导；第 2 步新增 ontology 下拉；第 3 步改为"安全+接口"；**新增步骤 4 "心智灯·认知引擎"**；**新增步骤 5 "预校验"**；步骤 6 保存 |
| `ecos_frontend/src/pages/scenario/MentalModelStep.tsx`（新建） | **单 base mind 表单**：假设/证据/不确定性判断/模型多选（变体 mind 创建留 P3b T27） |
| `ecos_frontend/src/pages/scenario/PreValidateStep.tsx`（新建） | 预校验 UI：六类资源 resolve 结果（PASS/FAIL 红绿）+ AI 工具清单 + security 预演 + 心智档校验 |
| `ecos_frontend/src/pages/scenario/ResourcePickerStep.tsx`（新建） | 步骤 2 四类资源（数据源/本体实体/知识资产/智能体）并列选择器，选项池从 `/available/*` 拉取（替换 `data.ts`） |
| `ecos_frontend/src/api.ts` | 新增 10 个 API 函数（6 个 available + POST/GET/DELETE mind + pre-validate） |
| `ecos_frontend/src/locales/scenario/{zh-CN,en}.json` | 新增 namespace `scenario.avl.*`（可用资源）、`scenario.mind.*`（心智档）、`scenario.prv.*`（预校验）三个 namespace（**遵守前端铁律 §4.3**） |

> **P3b 追加文件/命名空间（T27/T28）**：
> | 文件 | 操作 |
> |---|---|
> | `ecos_frontend/src/pages/scenario/MindVariantStep.tsx`（P3b 新建，见 T27） | 多心智变体管理 Tab：列表 + 加变体 Modal + active 切换 |
> | `ecos_frontend/src/pages/scenario/CognitionPanel.tsx`（改造，P3b 见 T28） | 新增「对比推演」区（A/B mind 双栏 diff） |
> | `ecos_frontend/src/locales/scenario/{zh-CN,en}.json`（追加） | 新增 `scenario.variant.*`（变体管理）/ `scenario.diff.*`（对比推演）两个 namespace |

### 2.9 全链路串通（P2）

```
前端 ScenarioEditor
  → POST /api/v1/workspace/scenarios/{id}/mind-model      （建 base mind；变体走 /minds POST）
    → workspace-impl → PG ecos_scenario_mind（cognitive 表认知心智·1:N 行）
    → 同步 cognitive-engine REST
       → POST /api/v1/cognitive/evidence/hypotheses/beliefs （已实现 P2a）
  → POST /api/v1/workspace/scenarios/{id}/pre-validate
    → workspace-impl 并行调 4 service（datanet/buszhi/dccheng/aiming）
    → 联调 security-engine 预演
    → 返回 {dataset PASS/FAIL, ... 共 6 项 + 心智档校验 + active_mind 唯一性}
     前端渲染红绿徽标；不阻塞 DRAFT，ACTIVE 需全 PASS
  → 运行态（P3b）：POST /scenarios/{id}/cognitive/{diagnose|forecast|simulate|policy}?mind={mindId?}
     → workspace 读指定 mind 三要素 → 透传 cognitive:18089 → 瞬态返回
     → 策略输出可选落 ecos_decision_record（含 source_refs[] hash 溯源）
```

### 2.10 设计判断：心智管理是否独立于场景定义（ADR-PMO60-1 依据）

| 方案 | 形态 | 判定 |
|:--:|---|:--|
| A. 内嵌 1:1 | `UNIQUE scenario_id`，心智随场景生死 | ❌ 多心智变体诉求（Q4）无法表达；心智与场景生命周期耦合 |
| **B. 1:N + 独立 REST 子资源** | `UNIQUE (scenario_id, mind_label)` + `active_mind` 哨兵 + `/minds` 独立 CRUD | ✅ **采纳**：心智 = cognitive 域自治资产，场景 = 业务引用者；单表多行满足"同场景多档心智" |
| C. 拆独立心智主表 + 桥表 | `ecos_mind`（跨场景）+ `ecos_mind_ref(scenario_id, mind_id)` | ⏸ 过度：反向多对多（同心智跨多场景）是**未来需求**，今天 Q4 只需"同场景多档"，1:N 已达成，待真出现再补桥表 |

**采纳 B 的 3 条理由**：
1. **单一职责**：场景表职责是"业务对象聚合"（名/目的/binding/优先级）；认知过程归 cognitive 域。1:N + 独立 REST 让二者各自演进不互相拖
2. **多心智变体零成本**：同 scenario 多 mind 行即"乐观/保守档"，`activate` 切换靠 partial unique index 兜底，无分布式锁
3. **最小前沿**：不预埋反向多对多桥表，让"同心智跨多场景复用的未来需求"**不阻塞当前落地**；出现时补 `ecos_mind_ref` 成本可控（本节明确不阻塞）

> **cognitive 引擎侧零感知**：只认 `mind_id` 入参。workspace 决定"哪档心智"（业务决策），cognitive 只负责"给这档心智算四件套"（认知能力）。职责边界清晰。

## §Task（按 P0.1 / P0.2 / P1 / P2 分阶段）

| 阶段 | Task | 文件 | 操作 | 验收 |
|:--:|:--|---|---|---|
| **P0.1** | T1 | 本报告 | 契约侦察交付 | ✔ 已完成（见 §P0.1） |
| **P0.2** | T2 | 本报告 | 心智模型 + 流程终稿 | ✔ 本次交付 |
| **P1** | T3 | `gateway/src/main/resources/db/migration/V130__ecos_scenario_mind.sql` | 新建 `ecos_scenario_mind` 表（§2.2，**1:N 多心智变体**：`mind_label` + `active_mind` + 3 个 partial/unique 索引） | 在 `sys_man` 建表成功；同一 scenario 插 2 行不同 mind_label OK、第 2 行 active=1 时触发 `uq_mind_active` 冲突拒绝 |
| | T4 | `gateway/.../V131__ecos_scenario_binding_upgrade.sql` | `ALTER TABLE ... ADD COLUMN target_id/target_type` + 索引 | 老 binding 读不受影响；新 binding 写 target_id 不报 NOT NULL |
| | T5a | `services/sysman/impl/sysman-boot/.../migration/V132__ecos_security_policy.sql` | 新建 `ecos_security_policy` 表 | 唯一约束 `name` 命中；`policy_expr` 字段返回 |
| | T5b | `services/sysman/impl/sysman-boot/.../migration/V133__ecos_interface_ref.sql` | 新建 `ecos_interface_ref` 表 | 唯一约束、接口 `endpoint`/`method` 字段 |
| | T6 | `services/sysman/impl/.../sysman/controller/SecurityPolicyController.java`（新建） | `@RequestMapping("/api/v1/security/policies")` CRUD 5 端点 | `GET /api/v1/security/policies` 返 JSON 列表；POST 创建走 security-engine 审计 Kafka |
| | T7 | `services/sysman/impl/.../sysman/controller/InterfaceRefController.java`（新建） | `@RequestMapping("/api/v1/interfaces")` CRUD 5 端点 | 同 T6 |
| | T8 | `workspace/.../workspace/controller/ScenarioOptionsController.java`（新建） | 6 个 `/scenarios/available/*` 联查端点（§2.5） | 任一端点 200 返数组（item id 为真 PG 主键）；断网时**默认 DENY**（§2.4）返回 503 |
| | T9 | `workspace/.../workspace/scenario/ScenarioMindService.java`（新建） | 落 `ecos_scenario_mind` **1:N** 写/读/删（minds CRUD：create base/variant、activate 切换、delete 补位）+ 同步 cognitive-engine REST（`/api/v1/cognitive/evidence\|hypotheses\|belief`）；应用层强校验 active_mind ≤1 | 建 base + 加 optimistic 变体后 2 行；对 optimistic 设 active=1 → base 自动 active=0（partial unique index 兼守护）；1 次 POST 在 cognitive 三表各看到 1 条新记录 |
| | T10 | `workspace/.../workspace/scenario/ScenarioPreValidateService.java`（新建） | 一键预校验：4 类 resource resolve + 2 类 system resolve + AI tool 匹配 + 心智档校验；返回逐项 `PASS/FAIL` 列表 | 完整 PASS 全绿；某个 service 不可达 = 该 service 一类 **FAIL**（不假绿） |
| | T11 | `workspace/.../workspace/scenario/ScenarioService.java` | 改 `saveBindings`：校验 `target_id + target_type` 一致性；6 类全部必填 `target_id`（安全/接口 P1 已完成）；调 `kafkaProducer.send(auditTopic)` 发审计 | 不通过校验时 `BAD_REQUEST`；审计 Kafka 在 `sysman` 侧可见 |
| | T12 | 三滤波器（架构铁律 §1.2） | `VersionPrefixRewriteFilter` + `ClearanceInterceptor` 豁免 + `SecurityConfig.permitAll` 各加 `/api/v1/workspace/scenarios/**`、`/api/v1/security/policies/**`、`/api/v1/interfaces/**` | curl 经 gateway 8080 不需 JWT 也能 200（demo 期；生产期由 OPA 承接） |
| | T13 | `ecos_frontend/src/api.ts` | 新增 10 个 API 函数（§2.5） | tsc --noEmit EXIT=0 |
| **P2** | T14 | `ecos_frontend/src/pages/scenario/ResourcePickerStep.tsx`（新建） | 替换 data.ts mock，拉 `/available/*`，4 栏并列 | 数据源下拉项 `/api/v1/datanet/datasource` 真 `datasourceId` |
| | T15 | `ecos_frontend/src/pages/scenario/MentalModelStep.tsx`（新建） | **单 base mind 表单**：假设 3 条制 + 证据 + 不确定性判断（本地预校验 prob 和=1）+ 模型多选；四件套开关（diagnose/forecast/simulate/policy）默认开/关 + 权重滑块 | 概率和≠1 时红字 + POST 预检查 /mind-model；变体 mind 加减按钮**置灰**（P2 只 base，标注「P3b 开放」） |
| | T16 | `ecos_frontend/src/pages/scenario/PreValidateStep.tsx`（新建） | 六类红绿徽标 + AI tool 匹配 + 心智档校验 + "拟 READY" 保存为 ACTIVE 按钮 | 保存时若全 PASS 自动状态 ACTIVE；不通过仅 DRAFT |
| | T17 | `ecos_frontend/src/pages/scenario/ScenarioEditor.tsx` | 7 步→6 步；4/5 步插入；核对 `Locale = 'zh'|'en'` 无硬编码 | 前端五铁律 0 命中；切中/英 6 步步骤标签全切换 |
| | T18 | `ecos_frontend/src/locales/scenario/{zh-CN,en}.json` | 新增 `scenario.avl.*`（可用资源）、`scenario.mind.*`（心智档）、`scenario.prv.*`（预校验）三个 namespace | 中英文 key 对称；100% 切换无 i18n fallback 字 |
| | T19 | `ecos_frontend/src/locales/scenario/i18n.test.tsx`（新建，vitest） | 组件级对 6 步向导文案 zh/en 互切；断言 `knowledge.group` 类**不再走 `locale === 'zh-CN'`**（上一文件已修，本批次防回归） | 2 用例全 PASS；变异测试（删掉 `t()` 改回硬编码）必 FAIL |
| **P3** | T20 | `ecos_frontend/src/pages/scenario/ScenarioE2E.test.tsx`（新建,vitest + jsdom） | 端到端：选 4 类资源 + 设心智档 + pre-validate 全 PASS → POST 场景 ACTIVE | 200；`ecos_business_scenario.status='ACTIVE'`；`ecos_scenario_mind` 1 条；cognitive 3 表各 +1 |
| **P3** | T21 | 浏览器 E2E（浏览器工具） | 渲染 + console + network 截屏 | 三项无红 |
| **P3b** | T24a | `workspace/.../workspace/controller/ScenarioMindController.java`（新建） |  minds 子资源 CRUD：`GET/POST /scenarios/{id}/minds`、`PATCH/DELETE /scenarios/{id}/minds/{mindId}`（§2.7 表 P3b 4 行）；activate 切换事务内 forte 归零其它变体 | curl：建 base→加 optimistic→PATCH optimistic.active=1 后 base.active=0；删 base 后 optimistic 自动补位 active=1 |
| **P3b** | T25 | 4 个场景级 cognitive 端点（§2.7 表内 POST 行 4 行，含 `?mind={mindId?}`） | 扩展 workspace-impl，新增 `ScenarioCognitiveController` 中转 cognitive engine；读指定（或 active）mind 的 `cognitive_endpoints` 校验 `enabled` → 透传三要素 + 模型引用 → cognitive 端点 | 对启用端点 POST → 返回 root_causes/forecast/simulate/policy 各自结构体；`simulate` 未启用返 `409{code:409,msg}`；`?mindA&mindB` 对比推演双行返回 |
| **P3b** | T26 | 四件套返回前端渲染 + 决策回执 Preview | 运行态只读调 P3b T25 端点渲染、策略输出汇总到 `DecisionService`（可选落盘 `ecos_decision_record` V134，字段含 `action_plan` JSONB + `source_refs[]`(指 diagnose/forecast/simulate 输出 hash)） | 4 个端点输出卡片化展示；任一 `enabled=false` 时 UI 灰置；`ecos_decision_record`（若有）可回查 source_refs 溯源 |
| **P3b** | T27 | `ecos_frontend/src/pages/scenario/MindVariantStep.tsx`（新建，多心智变体管理） | 场景详情新增 Tab「心智变体」：列表（每 mind 一行：label/active/四件套摘要/三要素摘要）+「+ 加变体」→ 弹 Modal 填 mind_label / 继承 base 拷贝 → 独立编辑三要素 / 四件套权重 → active 切换 radio；删除 base 时自动补位确认 | 场景可挂 N 个 mind；任一 mind 可独立 CRUD；activate 切换瞬间其它 mind 归 0（对齐 T24a 后端契约）；删除 base 后 last-verified 场景让 optimistic 自动变 active |
| **P3b** | T28 | 两 mind 对比推演 UI（P3b T25 的 `?mindA&mindB` 消费方）| 在 CognitionPanel 新增「对比推演」区：选 2 个 mind 作 A/B，`POST /scenarios/{id}/cognitive/simulate?mindA=...&mindB=...` 返回 → 左右双栏 diff 视图（同 3 要素下两档预测对比表）+ 归一化置信度对比条 | 双行结构体并列渲染；prob 差异列高亮；某一 mind 未启用 simulate 时返 409 且 A/B 栏置灰；i18n 中英文对称；对比推演**只读**不落盘 |
| **P4** | T22 | `docs/ARCHITECTURE-RULES.md` §4.8 增补 | 加 "binding.target_ref 真 ID 化" 条款 | 文档同步 |
| | T23 | `ecos_frontend/src/pages/scenario/README.md`（新建，仅本文档） | 创建流程说明 + 预校验 gate | 文档可读 |
| | T24 | Reviewer 审查 + 全量回归 | Reviewer 6 专项（code-review/security/recommender/test/review）+ 6 模块 `mvn install -DskipTests` + `vitest` 全绿 | deliverable_allowed=true |

## §验收四步法（每阶段必跑）

```
V1: 文件生存检查（替代 ls -lt | head，本次用 git diff --stat）
V2: 集成点 grep（add 的 import / 注册的 Filter / 调用的 endpoint 反向引用）
V3: 编译门（后端 mvn install -DskipTests；前端 tsc --noEmit + vite transform）
V4: 浏览器 E2E（P3 T21）：渲染 + console + network 截屏三项无红
```

## §风险登记

| # | 风险 | 缓解 |
|:--:|---|---|
| 1 | **V130~V133 多个 migration 同冲突序号**（当时 V112 是 cognitive 的） | 本次用 **V130/V131（workspace）+ V132/V133（sysman-boot）**，避开已用 124/126/127~134/136~143 区间； Flyway disabled 时手工执行，顺序靠文件序号自然 |
| 2 | `ecos_scenario_binding.target_ref` 兼容读取 | 老代码 `ScenarioService.bindings()` 仍读 string（保留字段），老前端**不被强制升级**时不失效；新前端从 `target_id` 取 |
| 3 | **2 类 P1 新表（security/interface）deploy 时序** | 同 sysman-boot 一次 Flyway 链跑，不存在单独 deploy；workspace 调 `sysman:18081` 跨服务 REST，**默认 DENY**（§2.4） |
| 4 | cognitive-engine 表**只读复用**（ADR-9）| `ecos_scenario_mind.evidence_refs` 等是 **id 引用**，不 ALTER cognitive 三表，不破坏 ADR-9 |
| 5 | 跨服务 6 路联查 N+1 | `ResourcePicker` 拖到 workspace 层并发 `RestTemplate` 6 路，前端单次 POST 不穿透，无 N+1 |
| 6 | AI Agent 能力清单不在 schema | `GET /api/v1/agent/tools` 是**运行时内置注册**（ToolRegistry），所以 Agent 栏显示工具清单时由**前端实时拉**，不落库 |
| 7 | **inverse risk**: 用户存 ACTIVE 但 mental model 引的 evidence 时环境已变（例：关联知识库被删） | `pre-validate` 必查引用可达 + `ecos_scenario_mind.model_refs` 空时 WARN 而非 FAIL；定期 nightly 重新 pre-validate |

## §回滚

1. **DDL 回滚**：`DROP TABLE ecos_scenario_mind, ecos_security_policy, ecos_interface_ref;` `ALTER TABLE ecos_scenario_binding DROP COLUMN target_id, DROP COLUMN target_type;`（每次 migration 独立回滚脚本）
2. **代码回滚**：revert 涉及 commit hash（每个 Task 独立提交，单 Task 回退不级联）
3. **数据回滚**：老 binding 未动；新表 `ecos_scenario_mind` 删除即清；`ecos_security_policy`/`ecos_interface_ref` 独立删表
4. **前端回滚**：`data.ts` mock 保留，回退 `ResourcePickerStep` 不加载则自动 fallback 到 mock

## §变更日志

| 版本 | 日期 | 变更 | 原因 |
|:--:|:--:|---|---|
| v1.0 | 2026-09-21 | 初版 P0.1 契约 + P0.2 心智模型冻结 | 规划启动 |
| v1.1 | 2026-09-21 | 新增 **§2.1 认知心智层概念模型**（三要素 + 四件套端点 + 模型底座 + 决策层）；§2.2 `cognitive_endpoints` JSONB 显式四键 schema；§2.5 步骤 4 新增四件套开关预设为默认；§2.5 步骤 5 新增四件套可达性检查；§2.6 端点表新增 POST `/{id}/cognitive/{diagnose,forecast,simulate,policy}`；P3b T25/T26 新增 2 个 Task | **用户反馈**：场景不只是"绑资源"，还要在运行态能调用 cognitive 四件套端点（含原未提及的 `/simulate` 与 `/policy`）；端到端"创建心智 → 运行时推理/预测/推演/策略"需可同迭代内闭环 |
| v1.2 | 2026-09-21 | §2.1 补「**策略非三要素**」+「**KB 与证据解耦**」两条脚注（Q1/Q2）；新增 **§2.3 语义模型**（四层「窗/房/活动/光」+ Q3 场景关系 + **Q4 企业投资决策全例**）；§2.4~2.9 重编号；P3b T26 的 `ecos_decision_record` 补 `source_refs[]` 溯源字段 | **用户四问对齐**：①证据与知识库关系 ②策略是否为要素 ③认知与场景关系 ④企业决策场景举例；四问答案落文档，供跨团队宣讲 |
| v1.3 | 2026-09-21 | **心智改 1:N 多变体**（`mind_label` + `active_mind` + partial unique index）+ **新增 §2.10 设计判断（ADR-PMO60-1：心智独立于场景定义，1:N+独立REST子资源，不建桥表）**；§2.3 关系表 1:1→1:N；§2.7 端点新增 `/minds` 子资源 4 个 CRUD + 4 个 cognitive 端点加 `?mind={mindId?}`；流程步骤 4/5 补变体与 active 唯一性；Task 新增 T24a（minds CRUD）+ T25 改造 | **用户拍板「多心智变体」+ 追问软件设计层面「心智是否独立于场景定义」**；架构判断采纳方案 B（1:N 独立子资源，不桥表化），同步落 DDL/REST/Task |
| v1.4 | 2026-09-21 | P2 重新定位为「**单 base mind 向导**」（T15 表单 + 变体按钮置灰标注 P3b）；P3b 新增 T27（`MindVariantStep.tsx` 多心智变体管理 Tab）+ T28（CognitionPanel A/B 对比推演 `?mindA&mindB`）；§2.8 文件清单追加 P3b 2 文件 + `scenario.variant.*`/`scenario.diff.*` 两个 namespace | **用户确认「P2 向导内要多档对比 UI」**；把范围拆清楚：P2 = base 向导（不搞变体搞乱），P3b = 变体管理 + 对比推演独立 Task |

## §依赖与协作

- **Reviewer 六个专项必须全部触发**（code-review / security / recommendation / test / review / recommender），**禁止跳过**
- **Arch 二次审核**：T3~T7 的 DDL 提交前先给 `architect` 过一遍（尤其 V130 与 cognitive 三表的 ADR-9 对齐）
- **QA 全量测试**：P2 完成后跑 `vitest` + Playwright E2E
- **全架构回归**：依赖 `common-api` 的 service 改动前后跑 `mvn install -DskipTests` 一次

## §交付凭证

每项 Task 完成时产出：`docs/03开发阶段/03-02-升级设计/t_<task_id>_task_card.md`（含 commit hash / curl 验收 / Reviewer 报告 id），并按 `统一验收流程` 自动流转。
