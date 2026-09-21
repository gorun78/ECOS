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

### 2.2 心智层数据模型（V130__ecos_scenario_mind.sql）

```sql
CREATE TABLE IF NOT EXISTS ecos_scenario_mind (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id           VARCHAR(64) NOT NULL UNIQUE,
    initial_belief_jsonb  JSONB DEFAULT '{}'::jsonb,   -- 不确定性判断初始化（{variable, prob[]}）
    evidence_refs         JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_evidence.id 数组
    hypothesis_refs       JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_hypothesis.id 数组
    model_refs            JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_model.id 数组（PMO-51 预测底座）
    cognitive_endpoints   JSONB DEFAULT '{}'::jsonb,   -- 四件套端点启停 + 权重
    -- cognitive_endpoints 期望 schema：
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
CREATE INDEX IF NOT EXISTS idx_mind_scenario ON ecos_scenario_mind (scenario_id);
```

> 设计要点：
> - 1:1（`UNIQUE scenario_id`），`ecos_business_scenario.id` → `ecos_scenario_mind.scenario_id` 单向，cognitive 写入不动场景表
> - **4 个 JSONB 字段**承载引用（不建 6 张关联表，避免过度工程化；引用是**证据/假设/模型 任一**可挂载多个）
> - `cognitive_endpoints` JSONB 内部四键 `diagnose/forecast/simulate/policy`，每键 `{enabled, weight, ...}`，P1 新增时仅改 JSON 内字段，**不动 DDL**

### 2.3 语义模型：场景是「窗」，心智是「房」，推演是「活动」，外部源是「光」

> 回答「认知与场景的关系（Q3）」防反模式：**场景不生成认知**，认知生成于场景外（证据从 PD/KB/Agent 流入），场景只是**聚合 + 唱例 + 授权 + 审计挂钩**。

| 层 | 关系 | 含义 |
|:--|---|---|
| **结构** | 场景 1:1 心智 | 创建场景自动生成 1 个 mind（`UNIQUE scenario_id`）；改场景名/优先级**不**必然改 mind |
| **数据** | 场景 1:N binding（6 资源）；场景 1:1 mind | binding=资源清单，mind=认知清单，**二者并列独立** |
| **运行时** | mind 是四件套端点的 `inline_context` 入口 | `POST /scenarios/{id}/cognitive/*` → workspace 读 mind 三要素 → 透传 cognitive:18089 → 瞬态返回 |
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
│ （P1 升真）   │   不确定性判断 + 关联认知模型）
├──────────────┴───────────────────────────────────────────┤
│ 5. **预校验闸**（NEW：跨服务 resolve 真 ID 可达 + 重表校验 +
│   security 审计 + AI 卡链路规约）
└────────────────────────────────────────────────────────────┘
        ↓ 通过
   坐库 ecos_business_scenario + mind + binding
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
| POST | `/api/v1/workspace/scenarios/{id}/mind-model` | 落 `ecos_scenario_mind` |
| GET | `/api/v1/workspace/scenarios/{id}/mind-model` | 读回 |
| DELETE | `/api/v1/workspace/scenarios/{id}/mind-model` | 删 |
| POST | `/api/v1/workspace/scenarios/{id}/pre-validate` | 一键预校验（返回逐项 PASS/FAIL） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/diagnose` | **P2 新增**：场景级诊断（读心智三要素 → 因果推理 → 根因链） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/forecast` | **P2 新增**：场景级预测（指定 `model_id` → 时序 + 置信区间） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/simulate` | **P2 新增**：场景级推演（反事实·`counterfactors[]` → 连锁影响） |
| **POST** | `/api/v1/workspace/scenarios/{id}/cognitive/policy` | **P2 新增**：场景级策略建议（四件套汇总 → `action_plan[]` + 风险） |

> 端点铁律：路径用 `/api/v1/` 前缀 + `VersionPrefixRewriteFilter` 的 `V1_REWRITE_MAP` + `SecurityConfig.permitAll` + `ClearanceInterceptor` 豁免 三滤波器逐层加（架构铁律 §1.2）。

### 2.8 前端文件改动清单（P2）

| 文件 | 操作 |
|---|---|
| `ecos_frontend/src/pages/scenario/ScenarioEditor.tsx` | 7 步→6 步向导；第 2 步新增 ontology 下拉；第 3 步改为"安全+接口"；**新增步骤 4 "心智灯·认知引擎"**；**新增步骤 5 "预校验"**；步骤 6 保存 |
| `ecos_frontend/src/pages/scenario/MentalModelStep.tsx`（新建） | 心智档表单：假设/证据/不确定性判断/模型多选 |
| `ecos_frontend/src/pages/scenario/PreValidateStep.tsx`（新建） | 预校验 UI：六类资源 resolve 结果（PASS/FAIL 红绿）+ AI 工具清单 + security 预演 + 心智档校验 |
| `ecos_frontend/src/pages/scenario/ResourcePickerStep.tsx`（新建） | 步骤 2 四类资源（数据源/本体实体/知识资产/智能体）并列选择器，选项池从 `/available/*` 拉取（替换 `data.ts`） |
| `ecos_frontend/src/api.ts` | 新增 10 个 API 函数（6 个 available + POST/GET/DELETE mind + pre-validate） |
| `ecos_frontend/src/locales/scenario/{zh-CN,en}.json` | 新增 namespace `scenario.mind.*`/`scenario.prv.*`/`scenario.avl.*`（**遵守前端铁律 §4.3**） |

### 2.9 全链路串通（P2）

```
前端 ScenarioEditor
  → POST /api/v1/workspace/scenarios/{id}/mind-model
    → workspace-impl → PG ecos_scenario_mind（cognitive 表认知心智）
    → 同步 cognitive-engine REST
       → POST /api/v1/cognitive/evidence/hypotheses/beliefs （已实现 P2a）
  → POST /api/v1/workspace/scenarios/{id}/pre-validate
    → workspace-impl 并行调 4 service（datanet/buszhi/dccheng/aiming）
    → 联调 security-engine 预演
    → 返回 {dataset PASS/FAIL, ... 共 6 项 + 心智档校验}
     前端渲染红绿徽标；不阻塞 DRAFT，ACTIVE 需全 PASS
```

## §Task（按 P0.1 / P0.2 / P1 / P2 分阶段）

| 阶段 | Task | 文件 | 操作 | 验收 |
|:--:|:--|---|---|---|
| **P0.1** | T1 | 本报告 | 契约侦察交付 | ✔ 已完成（见 §P0.1） |
| **P0.2** | T2 | 本报告 | 心智模型 + 流程终稿 | ✔ 本次交付 |
| **P1** | T3 | `gateway/src/main/resources/db/migration/V130__ecos_scenario_mind.sql` | 新建 `ecos_scenario_mind` 表（§2.1） | 在 `sys_man` 建表成功；6 表（`ecos_business_scenario` + `ecos_scenario_binding` + `ecos_scenario_mind` + cognitive 3 表）联查 SELECT 成功 |
| | T4 | `gateway/.../V131__ecos_scenario_binding_upgrade.sql` | `ALTER TABLE ... ADD COLUMN target_id/target_type` + 索引 | 老 binding 读不受影响；新 binding 写 target_id 不报 NOT NULL |
| | T5a | `services/sysman/impl/sysman-boot/.../migration/V132__ecos_security_policy.sql` | 新建 `ecos_security_policy` 表 | 唯一约束 `name` 命中；`policy_expr` 字段返回 |
| | T5b | `services/sysman/impl/sysman-boot/.../migration/V133__ecos_interface_ref.sql` | 新建 `ecos_interface_ref` 表 | 唯一约束、接口 `endpoint`/`method` 字段 |
| | T6 | `services/sysman/impl/.../sysman/controller/SecurityPolicyController.java`（新建） | `@RequestMapping("/api/v1/security/policies")` CRUD 5 端点 | `GET /api/v1/security/policies` 返 JSON 列表；POST 创建走 security-engine 审计 Kafka |
| | T7 | `services/sysman/impl/.../sysman/controller/InterfaceRefController.java`（新建） | `@RequestMapping("/api/v1/interfaces")` CRUD 5 端点 | 同 T6 |
| | T8 | `workspace/.../workspace/controller/ScenarioOptionsController.java`（新建） | 6 个 `/scenarios/available/*` 联查端点（§2.5） | 任一端点 200 返数组（item id 为真 PG 主键）；断网时**默认 DENY**（§2.4）返回 503 |
| | T9 | `workspace/.../workspace/scenario/ScenarioMindService.java`（新建） | 落 `ecos_scenario_mind` 写/读/删 + 同步 cognitive-engine REST（`/api/v1/cognitive/evidence|hypotheses|belief`） | 1 次 POST 在 cognitive 三表各看到 1 条新记录（按 `scenario_id` 关联） |
| | T10 | `workspace/.../workspace/scenario/ScenarioPreValidateService.java`（新建） | 一键预校验：4 类 resource resolve + 2 类 system resolve + AI tool 匹配 + 心智档校验；返回逐项 `PASS/FAIL` 列表 | 完整 PASS 全绿；某个 service 不可达 = 该 service 一类 **FAIL**（不假绿） |
| | T11 | `workspace/.../workspace/scenario/ScenarioService.java` | 改 `saveBindings`：校验 `target_id + target_type` 一致性；6 类全部必填 `target_id`（安全/接口 P1 已完成）；调 `kafkaProducer.send(auditTopic)` 发审计 | 不通过校验时 `BAD_REQUEST`；审计 Kafka 在 `sysman` 侧可见 |
| | T12 | 三滤波器（架构铁律 §1.2） | `VersionPrefixRewriteFilter` + `ClearanceInterceptor` 豁免 + `SecurityConfig.permitAll` 各加 `/api/v1/workspace/scenarios/**`、`/api/v1/security/policies/**`、`/api/v1/interfaces/**` | curl 经 gateway 8080 不需 JWT 也能 200（demo 期；生产期由 OPA 承接） |
| | T13 | `ecos_frontend/src/api.ts` | 新增 10 个 API 函数（§2.5） | tsc --noEmit EXIT=0 |
| **P2** | T14 | `ecos_frontend/src/pages/scenario/ResourcePickerStep.tsx`（新建） | 替换 data.ts mock，拉 `/available/*`，4 栏并列 | 数据源下拉项 `/api/v1/datanet/datasource` 真 `datasourceId` |
| | T15 | `ecos_frontend/src/pages/scenario/MentalModelStep.tsx`（新建） | 假设 3 条制 + 证据 + 不确定性判断 + 模型多选；**本地预校验 prob 和=1** | 概率和≠1 时红字 + POST 预检查 /mind-model |
| | T16 | `ecos_frontend/src/pages/scenario/PreValidateStep.tsx`（新建） | 六类红绿徽标 + AI tool 匹配 + 心智档校验 + "拟 READY" 保存为 ACTIVE 按钮 | 保存时若全 PASS 自动状态 ACTIVE；不通过仅 DRAFT |
| | T17 | `ecos_frontend/src/pages/scenario/ScenarioEditor.tsx` | 7 步→6 步；4/5 步插入；核对 `Locale = 'zh'|'en'` 无硬编码 | 前端五铁律 0 命中；切中/英 6 步步骤标签全切换 |
| | T18 | `ecos_frontend/src/locales/scenario/{zh-CN,en}.json` | 新增 `scenario.avl.*`（可用资源）、`scenario.mind.*`（心智档）、`scenario.prv.*`（预校验）三个 namespace | 中英文 key 对称；100% 切换无 i18n fallback 字 |
| | T19 | `ecos_frontend/src/locales/scenario/i18n.test.tsx`（新建，vitest） | 组件级对 6 步向导文案 zh/en 互切；断言 `knowledge.group` 类**不再走 `locale === 'zh-CN'`**（上一文件已修，本批次防回归） | 2 用例全 PASS；变异测试（删掉 `t()` 改回硬编码）必 FAIL |
| **P3** | T20 | `ecos_frontend/src/pages/scenario/ScenarioE2E.test.tsx`（新建,vitest + jsdom） | 端到端：选 4 类资源 + 设心智档 + pre-validate 全 PASS → POST 场景 ACTIVE | 200；`ecos_business_scenario.status='ACTIVE'`；`ecos_scenario_mind` 1 条；cognitive 3 表各 +1 |
| **P3** | T21 | 浏览器 E2E（浏览器工具） | 渲染 + console + network 截屏 | 三项无红 |
| **P3b** | T25 | 4 个场景级 cognitive 端点（§2.6 表内 POST 行 4 行） | 扩展 workspace-impl，新增 `ScenarioCognitiveController` 中转 cognitive engine；读 `mind.cognitive_endpoints` 校验 `enabled` → 透传三要素 + 模型引用 → cognitive 端点 | 对启用端点 POST 引用数据 → 返回 root_causes/forecast/simulate/policy 各自结构体；`simulate` 未启用时返 `409 {code:409,msg}` |
| **P3b** | T26 | 四件套返回前端渲染 + 决策回执 Preview | 运行态只读调 P3b T25 端点渲染、策略输出汇总到 `DecisionService`（可选落盘 `ecos_decision_record` V134，字段含 `action_plan` JSONB + `source_refs[]`(指 diagnose/forecast/simulate 输出 hash)） | 4 个端点输出卡片化展示；任一 `enabled=false` 时 UI 灰置；`ecos_decision_record`（若有）可回查 source_refs 溯源 |
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

## §依赖与协作

- **Reviewer 六个专项必须全部触发**（code-review / security / recommendation / test / review / recommender），**禁止跳过**
- **Arch 二次审核**：T3~T7 的 DDL 提交前先给 `architect` 过一遍（尤其 V130 与 cognitive 三表的 ADR-9 对齐）
- **QA 全量测试**：P2 完成后跑 `vitest` + Playwright E2E
- **全架构回归**：依赖 `common-api` 的 service 改动前后跑 `mvn install -DskipTests` 一次

## §交付凭证

每项 Task 完成时产出：`docs/03开发阶段/03-02-升级设计/t_<task_id>_task_card.md`（含 commit hash / curl 验收 / Reviewer 报告 id），并按 `统一验收流程` 自动流转。
