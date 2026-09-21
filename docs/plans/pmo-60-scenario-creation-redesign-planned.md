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

### 2.1 新增数据模型（V130__ecos_scenario_mind.sql，落 PG `sys_man`，cognitive engine 独占）

```sql
CREATE TABLE IF NOT EXISTS ecos_scenario_mind (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scenario_id           VARCHAR(64) NOT NULL UNIQUE,
    initial_belief_jsonb  JSONB DEFAULT '{}'::jsonb,   -- 不确定性判断初始化（{variable, prob[]}）
    evidence_refs         JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_evidence id 数组
    hypothesis_refs       JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_hypothesis id 数组
    model_refs            JSONB DEFAULT '[]'::jsonb,   -- 引用 ecos_cognitive_model id 数组（PMO-51）
    cognitive_endpoints   JSONB DEFAULT '{}'::jsonb,   -- 认知服务权重（{diagnose:1.0, forecast:0.8}）
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
> - **4 个 JSONB 字段**承载引用（不建 6 张关联表，避免过度工程化；引用关系是**证据/假设/模型 任一**可挂载多个）
> - `cognitive_endpoints` JSONB 兼容未来新增运行类型（`simulate/quantify/impact-analysis/...`），无需再 DDL

### 2.2 升级 binding schema（V131）

```sql
ALTER TABLE ecos_scenario_binding 
  ADD COLUMN IF NOT EXISTS target_id   VARCHAR(64),
  ADD COLUMN IF NOT EXISTS target_type VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_scenario_bind_tid ON ecos_scenario_binding (target_id);
```

兼容策略：旧 binding `target_ref` 保留只读；新 binding 优先填 `target_id + target_type`，`target_ref` 仍写在 ID 字符串（保持兼容，老客户端可读）。

### 2.3 新建 2 张真资源表（P1 sysman，sys_man 库）

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

### 2.4 流程设计（新 7 步 → 新 6 步，心智层并行）

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
- 第 4 步：**NEW** — 心智档
  - 初始假设（3 条制，可加）→ 自动落 `ecos_cognitive_hypothesis`
  - 初始证据（可多源，可信度 0~1）→ `ecos_cognitive_evidence`
  - 不确定性判断（变量 + 离散概率分布 prob 和=1 **强校验**）→ `ecos_cognitive_belief`
  - 关联认知模型（`ecos_cognitive_model` id 多选）→ 写入 `ecos_scenario_mind.model_refs`
- 第 5 步：**NEW** — 一键预校验
  - 4 类真 ID resolve（DATASOURCE/ONTOLOGY_ENTITY/KNOWLEDGE_ARTICLE/AGENT）
  - 2 类真 ID（SECURITY_POLICY/INTERFACE）经 `sysman`
  - 联动 `GET /api/v1/agent/tools` 校验 Agent 能力闭环
  - security 链路预演（mock 1 条代表数据触发 security-engine 评估，**不落库**）
  - 心智档 `belief` 概率和=1 强校验 + 引用表存在性
  - **不通过项标记红字，允许保存为 DRAFT**（不阻塞创建，仅"READY"才允许 ACTIVE）
- 第 6 步：保存（替换原第 7 步"安全阻断"——独立拆出来更安全治理）

### 2.5 新增端点（workspace-scene :18090，P1）

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

> 端点铁律：路径用 `/api/v1/` 前缀 + `VersionPrefixRewriteFilter` 的 `V1_REWRITE_MAP` + `SecurityConfig.permitAll` + `ClearanceInterceptor` 豁免 三滤波器逐层加（架构铁律 §1.2）。

### 2.6 前端文件改动清单（P2）

| 文件 | 操作 |
|---|---|
| `ecos_frontend/src/pages/scenario/ScenarioEditor.tsx` | 7 步→6 步向导；第 2 步新增 ontology 下拉；第 3 步改为"安全+接口"；**新增步骤 4 "心智灯·认知引擎"**；**新增步骤 5 "预校验"**；步骤 6 保存 |
| `ecos_frontend/src/pages/scenario/MentalModelStep.tsx`（新建） | 心智档表单：假设/证据/不确定性判断/模型多选 |
| `ecos_frontend/src/pages/scenario/PreValidateStep.tsx`（新建） | 预校验 UI：六类资源 resolve 结果（PASS/FAIL 红绿）+ AI 工具清单 + security 预演 + 心智档校验 |
| `ecos_frontend/src/pages/scenario/ResourcePickerStep.tsx`（新建） | 步骤 2 四类资源（数据源/本体实体/知识资产/智能体）并列选择器，选项池从 `/available/*` 拉取（替换 `data.ts`） |
| `ecos_frontend/src/api.ts` | 新增 10 个 API 函数（6 个 available + POST/GET/DELETE mind + pre-validate） |
| `ecos_frontend/src/locales/scenario/{zh-CN,en}.json` | 新增 namespace `scenario.mind.*`/`scenario.prv.*`/`scenario.avl.*`（**遵守前端铁律 §4.3**） |

### 2.7 全链路串通（P2）

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
| | T21 | 浏览器 E2E（浏览器工具） | 渲染 + console + network 截屏 | 三项无红 |
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

## §依赖与协作

- **Reviewer 六个专项必须全部触发**（code-review / security / recommendation / test / review / recommender），**禁止跳过**
- **Arch 二次审核**：T3~T7 的 DDL 提交前先给 `architect` 过一遍（尤其 V130 与 cognitive 三表的 ADR-9 对齐）
- **QA 全量测试**：P2 完成后跑 `vitest` + Playwright E2E
- **全架构回归**：依赖 `common-api` 的 service 改动前后跑 `mvn install -DskipTests` 一次

## §交付凭证

每项 Task 完成时产出：`docs/03开发阶段/03-02-升级设计/t_<task_id>_task_card.md`（含 commit hash / curl 验收 / Reviewer 报告 id），并按 `统一验收流程` 自动流转。
