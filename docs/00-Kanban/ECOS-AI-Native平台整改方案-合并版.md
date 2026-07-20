# ECOS AI Native 平台整改方案（合并版）

> 合并 v1（智能体/Copilot/进化闭环设计） + v2（架构审计/数据分层/ToolRouter实体映射）
> 基于2026-07-18全量审计，一次性可执行。

---

## 零、架构现状：我们站在哪

### 0.1 核心资产清单

| 资产 | 位置 | 状态 |
|------|------|:--:|
| **services/ 层12微服务** | `services/{agent,ai,api-gateway,catalog,cognitive,identity,knowledge,object,ontology,workflow}` | ✅ 已建 |
| **agent-service 8子模块** | Planner / Executor / Evaluator / Governance / Memory(4层) / Reflection / Orchestration / ToolRouter / Telemetry | ✅ 框架完整 |
| **6引擎体系** | `engine/{security,data,ontology,cognitive,kb,ai}-engine/` | ✅ 全部实现IEngine |
| **Hermes引擎** | `runtime/hermes-engine/` | ✅ SessionManager + AgentScheduler + LLMGateway |
| **12域schema隔离** | `ecos_agent/knowledge/ontology/identity/catalog/mission/rule/object/workflow/audit/config/pipeline` | ✅ V47完成 |
| **Data Engine V2** | 14张新表 + 5大能力(统一查询/同步/管道/质量/清洗) | ✅ 架构就绪 |
| **AI工作台前端** | `pages/aiworkbench/` (7子Tab) + AgentStudio/Builder/Mesh/TestConsole | ✅ 基础UI |
| **MemoryContext** | `agent-service/.../model/MemoryContext.java` (4层:Working/Session/LongTerm/Enterprise) | ✅ 模型定义 |
| **ToolRouter** | `ToolRouterService` + `ecos_tool_definition` 表 | ⚠️ **stub**——返回ToolType枚举值，未对接实际工具 |
| **ICopilotService** | `common-api/.../ICopilotService.java`（code-gen模式） | ⚠️ 只做NL→SQL/Pipeline，不做对话Agent |

### 0.2 四个关键断点

| # | 断点 | 现象 |
|---|------|------|
| 🔴1 | **ToolRouter未对接** | `getAvailableTools()`返回 `[api,function,knowledge]`，不返回任何真实工作台功能 |
| 🔴2 | **无领域Agent定义** | agent_registry只有4个骨架Agent，缺System Prompt、Tool白名单、知识绑定 |
| 🔴3 | **Copilot未通agent-service** | ICopilotService只做code-gen，不调用Planner/Executor |
| 🔴4 | **无数据分层流** | Data Engine V2有管道能力，但缺少"源→近源→加工→应用"分层模型 |

### 0.3 三版本基础设施

| 版本 | 数据库 | 向量存储 | 适用场景 |
|------|--------|----------|----------|
| standard | PostgreSQL | **pgvector**（PG扩展） | 中小企业，PG单库全功能 |
| enterprise | PostgreSQL + Neo4j | **pgvector**（同standard） | 中型企业，因果链>3层启用图谱 |
| flagship | PostgreSQL + Neo4j + Doris | **Doris向量化**（替换pgvector） | 大型企业，单表>100万行启用列存+向量 |

---

## 一、整体路线：四周五支柱递进

```
Week 1:  数据分层流 + ToolRouter实体化（Pillar 1+2+3：底座）
Week 2:  五大领域Agent完整定义（Pillar 4：智能体）
Week 3:  Copilot统一沙箱挂载5工作台（Pillar 4：交互层）
Week 4:  自进化闭环组网（Pillar 5：认知生态）
```

---

## 二、Week 1：数据底座 + ToolRouter实体化

### 2.1 数据分层存储模型

作为数据产品，分层设计的核心原则：**每一层有明确的不可变契约——下层为上层提供数据，上层不修改下层。**

```
┌──────────────────────────────────────────────────────────────┐
│ L4 应用数据层 (Application)          ← Agent/Copilot直接消费  │
│   本体对象运行时 · 目标/KPI/场景 · 聚合视图 · 看板缓存         │
│   存储: PG (ecos_object, ecos_mission)                        │
│   特征: 读多写少, 预先关联好, 面向展示和决策                   │
├──────────────────────────────────────────────────────────────┤
│ L3 语义数据层 (Semantic)             ← 知识图谱+向量+规则     │
│   知识图谱(节点/边) · 向量嵌入 · 专家规则 · 业务术语表         │
│   存储: PG+pgvector, Enterprise加Neo4j, Flagship加Doris向量   │
│   特征: 实体关系完备, 支持语义搜索和推理                       │
├──────────────────────────────────────────────────────────────┤
│ L2 加工数据层 (Curated)              ← 清洗/标准化/关联后     │
│   质量验证通过的数据 · 去重/标准化/维度关联 · 质量报告         │
│   存储: PG (ecos_rule, ecos_pipeline)                         │
│   特征: 可信、一致、可追溯, Pipeline产物                      │
├──────────────────────────────────────────────────────────────┤
│ L1 近源数据层 (Landing/Raw)          ← 源系统数据的第一落脚点  │
│   源系统镜像表 · 文件存储(CSV/Excel/PDF) · 元数据采集快照      │
│   存储: PG (ecos_catalog) + MinIO(S3)                         │
│   特征: 不可变、全量保真、带时间戳                             │
├──────────────────────────────────────────────────────────────┤
│ L0 源数据层 (Source)                 ← ECOS管理范围之外       │
│   企业ERP/CRM/WMS/MES · SaaS API · IoT流数据                  │
│   通过 td_datasource 注册JDBC连接, Connector对接              │
│   特征: ECOS只读不写, 不修改源系统                             │
└──────────────────────────────────────────────────────────────┘
```

**数据流向**：
```
L0源系统 → [同步管道] → L1近源(原始副本) → [质量清洗] → L2加工(干净数据)
                                                              ↓
L4应用(看板/Agent消费) ← [本体映射] ← L3语义(图谱+向量+规则)
```

**三版本对各层的支撑**：

| 层 | Standard (PG) | Enterprise (+Neo4j) | Flagship (+Doris) |
|----|:---:|:---:|:---:|
| L4-应用 | PG `ecos_object/mission` | 同左 | 同左 |
| L3-语义 | PG存图+pgvector向量 | Neo4j存图 + pgvector向量 | Neo4j存图 + **Doris向量化** |
| L2-加工 | PG `ecos_rule/pipeline` | 同左 | Doris加速大规模清洗 |
| L1-近源 | PG `ecos_catalog` + MinIO | 同左 | Doris存海量近源数据 |
| L0-源 | JDBC/API只读 | 同左 | 同左 |

**与Palantir Foundry的对标**：L1=Raw Dataset, L2=Cleaned Dataset, L3=Ontology+Object Types, L4=Workshop/Apps。关键区别——Palantir用Spark做大规模ETL，ECOS用Pipeline+数据引擎V2做小规模ETL。

| 步骤 | 交付物 | 工期 |
|------|--------|:--:|
| D1.1 | `ecos_data_layer` 枚举（SOURCE/RAW/CURATED/APPLICATION） | 0.5d |
| D1.2 | `td_data_resource` 加 `layer` 列 | 0.5d |
| D1.3 | Pipeline任务根据类型自动标注layer | 1d |
| D1.4 | Data Engine `/api/v1/engine/data/layers` 端点 | 1d |
| D1.5 | `ecos-docker/docker-compose.yml` 加MinIO volume映射 | 0.5d |

### 2.2 ToolRouter实体化——20个Tool对接工作台

**当前**：`ToolRouterServiceImpl.getAvailableTools()` 返回 `ToolType` 枚举值。
**目标**：从 `ecos_agent.ecos_tool_definition` 表动态加载，反射调用对应Service Bean。

**统一契约**：

```java
// agent-service 新增
public interface ToolExecutor {
    Object execute(String toolCode, Map<String, Object> params);
}
// 各引擎实现，注册到 ToolRouter
```

**20个Tool**（逐个工作台梳理，覆盖所有Agent需要的调用能力）：

#### 数据工作台 Tool (#1-7)

| # | Tool Code | 委托Service | 对应已存在端点 | 用途 |
|:--:|-----------|------------|---------------|------|
| 1 | `ListDataSources` | `DataSourceRegistryService` (data-engine) | `GET /datanet/datasource` | 列出已注册数据源及连接状态 |
| 2 | `GetTableSchema` | `MetadataCollectionService` (data-engine) | `GET /datanet/metadata/fields/{resourceId}` | 获取物理表字段名/类型/主键/注释 |
| 3 | `SearchCatalog` | `CatalogDashboardService` (data-engine) | `GET /datanet/catalog/search?q=` | 按关键词/标签/域搜索数据目录 |
| 4 | `QueryPhysicalTable` | `QueryExecutionServiceImpl` (data-engine) | `POST /api/v1/engine/data/query/execute` | 执行SQL查询物理表 |
| 5 | `GetDataLineage` | `DataLineageService` (data-engine) | `GET /api/v1/engine/data/lineage/pipeline/{id}` | 追溯数据血缘，上游→下游 |
| 6 | `RunQualityCheck` | `QualityInspectionService` (data-engine) | `POST /api/v1/engine/data/quality/rules/{id}/evaluate` | 执行质量规则并返回评分 |
| 7 | `TriggerPipeline` | `PipelineExecutionEngine` (data-engine) | `POST /api/v1/engine/data/pipeline/tasks/{id}/run` | 触发管道任务执行 |

#### 本体工作台 Tool (#8-13)

| # | Tool Code | 委托Service | 对应已存在端点 | 用途 |
|:--:|-----------|------------|---------------|------|
| 8 | `ListObjectTypes` | `OntologyController` (ontology-engine) | `GET /api/v1/ecos/ontologies` | 列出本体内所有对象类型 |
| 9 | `GetObjectProperties` | `OntologyPropertyController` (ontology-engine) | `GET /api/v1/ecos/entities/{id}/properties` | 获取对象类型的属性定义 |
| 10 | `GetObjectRelationships` | `OntologyRelationshipController` (ontology-engine) | `GET /api/v1/ecos/entities/{id}/relationships` | 获取对象间的关联关系 |
| 11 | `ValidateMapping` | `OntologyMappingController` (ontology-engine) | `POST /api/v1/ontology/mappings` + verify | 校验本体→物理表映射是否正确 |
| 12 | `ExecuteAction` | `OntologyActionApiController` (ontology-engine) | `POST /api/v1/ecos/actions/{code}/execute` | 执行本体定义的Action |
| 13 | `SearchOntologyGraph` | `OntologyGraphController` (ontology-engine) | `GET /api/v1/engine/ontology/graph/full` | 本体图遍历，查找实体间路径 |

#### 知识工作台 Tool (#14-17)

| # | Tool Code | 委托Service | 对应已存在端点 | 用途 |
|:--:|-----------|------------|---------------|------|
| 14 | `SearchKnowledgeGraph` | `KnowledgeGraphServiceImpl` (kb-engine) | `GET /api/knowledge/search?q=` | 知识图谱语义搜索 |
| 15 | `FindGraphPath` | `KnowledgeGraphServiceImpl` (kb-engine) | `GET /api/knowledge/path?from=&to=` | 两实体间最短路径 |
| 16 | `ExecuteExpertRule` | `ExpertRuleServiceImpl` (kb-engine) | `POST /api/v1/kb/rules/{id}/execute` | 执行专家规则并返回推理结果 |
| 17 | `RAGQuery` | `KnowledgeRetrievalServiceImpl` (kb-engine) | `POST /api/v1/knowledge/rag` | 向量检索增强生成——输入问题返回topK文档块 |

#### 安全中心 Tool (#18-20)

| # | Tool Code | 委托Service | 对应已存在端点 | 用途 |
|:--:|-----------|------------|---------------|------|
| 18 | `GetSecurityProfile` | `SecurityConfigService` (security-engine) | `GET /api/v1/security/profile` | 获取当前用户安全上下文（角色/权限/属性） |
| 19 | `CheckPermission` | `PolicyEngineController`→`OpaPolicyService` | `POST /api/v1/policy-engine/evaluate` | 校验"谁对什么做什么"——返回allow/deny+原因 |
| 20 | `AuditAccessLog` | `AuditController` (security-engine) | `GET /api/v1/audit/logs` | 查询审计日志（按时间/主体/操作过滤） |

> **注意**：应用场景工作台的Tool（`GetWorldModelState`、`TraceCausalChain`、`ExecuteSimulation`）暂由认知引擎现有端点 `/api/v1/world-model/*` 覆盖，Phase 2再封装为独立Tool。

| 步骤 | 交付物 | 工期 |
|------|--------|:--:|
| T1 | `ToolExecutor` 接口（agent-service） | 0.5d |
| T2 | 20个Tool实现类，各自注入对应Service Bean | 2.5d |
| T3 | `ToolRouterServiceImpl` 改造（DB加载+ToolExecutor映射） | 1d |
| T4 | `ecos_tool_definition` 种子数据（20条INSERT） | 0.5d |
| T5 | `POST /api/v1/agent/tools/{code}/execute` 端点 | 0.5d |

### 2.3 Hermes MemoryContext 集成

**已有**：agent-service 的 `MemoryContext` 模型（Working/Session/LongTerm/Enterprise四层）。
**缺失**：`MemoryServiceImpl` 未持久化，`AgentScheduler` 未在调用链中读写记忆。

| 步骤 | 交付物 | 工期 |
|------|--------|:--:|
| M1 | `MemoryServiceImpl` 持久化实现（短期Redis/内存 + 长期 `ecos_agent.agent_memory` 表） | 1d |
| M2 | `ExecutorServiceImpl` 集成 Memory：每次Agent调用前后读写Working→Session→LongTerm | 0.5d |
| M3 | `AgentCallController` 新增 `POST /api/v1/agent/chat` 端点（带sessionId自动关联记忆） | 0.5d |

---

## 三、Week 2：五大领域智能体完整定义

### 3.1 设计原则

每个 Agent = System Prompt + Tool白名单 + 知识绑定 + 人格Profile。
注册到 `ecos_agent.agent_registry`（表已存在），通过 `AgentProfileController`（已有）管理。
执行走 `PlannerService.createPlan(Goal)` → `ExecutorService` → ToolRouter → 工作台Service。

### 3.2 五大智能体

#### 智能体1：数据智能体 `data-agent`

> **身份**：ECOS数据工程专家
> **Tool白名单**：`ListDataSources`, `GetTableSchema`, `SearchCatalog`, `QueryPhysicalTable`, `GetDataLineage`, `RunQualityCheck`, `TriggerPipeline`
> **知识绑定**：`ecos_catalog` 数据目录 + 表结构 + 字段元数据
> **触发场景**：用户问"有哪些数据源""XX表结构是什么""数据质量报告""跑一下同步管道"

**System Prompt**：
```
你是ECOS数据工程专家。你拥有以下能力：
1. 查询物理表数据（QueryPhysicalTable）
2. 获取表结构和字段信息（GetTableMetadata）
3. 查看数据同步任务状态（ListSyncTasks）
4. 获取数据质量报告（GetQualityReport）

工作原则：
- 回答必须基于当前数据目录的实际表结构，不得凭空猜测
- 查询前先获取表结构，确认字段存在再构造SQL
- 如果发现数据质量问题，主动标记并建议修复方案
- 每次回答末尾标注数据来源：{表名}.{字段名}[数据源: {datasource_name}]
```

#### 智能体2：本体智能体 `ontology-agent`

> **身份**：ECOS本体建模专家
> **Tool白名单**：`ListObjectTypes`, `GetObjectProperties`, `GetObjectRelationships`, `ValidateMapping`, `ExecuteAction`, `SearchOntologyGraph`
> **知识绑定**：`ecos_ontology` 本体模型 + `ecos_object` 对象运行时数据
> **触发场景**：用户问"供应商对象有哪些属性""合同和供应商什么关系""怎么建一个新对象类型"

**System Prompt**：
```
你是ECOS本体建模专家。你精通：
- ObjectType（对象类型）/ LinkType（关联类型）/ ActionType（动作类型）/ Function（计算属性）设计
- 领域驱动设计（DDD）：对象必须代表业务上有意义的概念
- 组合优于继承：用Interface多重继承（SportsArena extends Building, Schedulable），不是单体继承链
- DRY原则：出现两次先忍着，出现三次重构

工作原则：
- 建模建议必须引用现有Ontology中的实际对象类型和关系
- 新增对象类型前先检查是否已有等价对象
- 属性定义必须包含：名称、类型、是否必填、业务含义
- Action建议必须声明前置条件和副作用
```
**设计原则参考**（Palantir Ontology五原则）：DDD语义对象 / DRY(Rule of Three+派生属性+接口) / 开闭原则 / 组合优于继承 / PECS协变逆变

#### 智能体3：知识智能体 `knowledge-agent`

> **身份**：ECOS知识工程专家
> **Tool白名单**：`SearchKnowledgeGraph`, `FindGraphPath`, `ExecuteExpertRule`, `RAGQuery`
> **知识绑定**：`ecos_knowledge` 知识图谱 + `ecos_rule` 专家规则 + 向量库
> **触发场景**：用户问"供应商华强钢构的风险""合同违约条款""因果追溯根因"

**System Prompt**：
```
你是ECOS知识工程专家。你拥有以下能力：
1. 知识图谱语义搜索（SearchKnowledgeGraph）——沿实体和关系追溯
2. 专家规则推理（QueryExpertRules）——IF-THEN规则引擎
3. 向量相似度检索（RetrieveVectors）——基于语义匹配查找相关知识

工作原则：
- 回答必须引用知识图谱中的实体和关系，标注实体ID
- 规则推理结果必须显示匹配的规则条件和置信度
- 向量检索结果按相似度排序，标注向量模型和topK
- 当知识图谱和规则引擎结论冲突时，同时呈现双方结论并标注分歧点
```

#### 智能体4：安全智能体 `security-agent`

> **身份**：ECOS安全合规专家
> **Tool白名单**：`GetSecurityProfile`, `CheckPermission`, `AuditAccessLog`
> **知识绑定**：`ecos_identity` 安全策略 + `ecos_audit` 审计日志 + OPA策略库
> **触发场景**：用户问"谁能看财务数据""这个操作合规吗""有没有越权访问"

**System Prompt**：
```
你是ECOS安全合规专家。你拥有以下能力：
1. 部署行级安全策略（DeployRowLevelPolicy）
2. 查询审计日志（AuditAccessLog）

工作原则：
- 任何操作建议必须附带权限校验——"谁（subject）对什么（object）做什么（action）"
- 审计日志查询必须标注时间范围和操作主体
- 安全策略修改必须遵循最小权限原则
- 发现越权行为时，先隔离再报告，不做自动修复
```

#### 智能体5：场景智能体 `scenario-agent`

> **身份**：ECOS业务场景专家
> **Tool白名单**：`ExecuteSimulation`
> **知识绑定**：`ecos_mission` 目标/场景/世界模型
> **触发场景**：用户问"如果换供应商影响多大""暑运雷雨改派航班方案"

**System Prompt**：
```
你是ECOS业务场景专家。你擅长：
- 蒙特卡洛仿真：基于历史数据分布生成N种可能结果
- 因果推理：沿因果链追溯根因
- 情景推演：给定假设条件，推演业务影响

工作原则：
- 仿真结果必须包含：场景ID、置信度、关键假设
- 推演必须指明因果链上的关键节点和断点
- 方案建议必须包含风险提示和回退策略
```

### 3.3 AgentMesh路由升级

**当前**：`AgentMeshServiceImpl.routeIntent()` 用关键词 `contains` 匹配。
**升级**：LLM语义路由 → 分析用户意图 → 语义匹配Agent能力描述 → 返回 targetAgentId + confidence + reasoning。

| 步骤 | 交付物 | 工期 |
|------|--------|:--:|
| A1.1 | 5个Agent System Prompt写入 `ecos_agent.agent_registry` | 0.5d |
| A1.2 | `AgentMeshServiceImpl` 新增 `routeIntentByLLM()` 方法 | 1d |

---

## 四、Week 3：Copilot统一沙箱挂载

### 4.1 CopilotAgentService（对话式Agent接口）

**当前**：`ICopilotService` 是code-gen接口（`generateSql/generatePipeline/suggestExpression/generateUdf/diagnose`）。

**新增**：对话式Agent接口：

```java
// common-api 新增
public interface ICopilotAgentService {
    CopilotResponse chat(String agentId, String userMessage, String sessionId);
    List<QuickQuestion> getQuickQuestions(String agentId);
}
// CopilotResponse: { answer, thoughtChain[], toolCalls[], sources[] }
```

**实现**：`agent-service` 新增 `CopilotAgentServiceImpl`：
1. 接收用户消息 → `AgentMeshService.routeIntent()` 路由到对应Agent
2. `PlannerService.createPlan(Goal)` 拆解任务
3. `ExecutorService` 执行 → ToolRouter调用Tool
4. `ReflectionService` 反思结果 → `EvaluatorService` 评分
5. 返回 `CopilotResponse`（含完整思考链）

### 4.2 前端CopilotPanel

**组件**：`components/CopilotPanel.tsx`（接收 `agentType` prop）

**核心功能**：
- 快捷问题按钮（预设高频问题）
- Thought-Action 思考链可视化（显示Agent内部推理→Tool调用→结果）
- Tool调用日志（每次Tool调用显示输入/输出/耗时）
- 一键导出对话

**5工作台挂载**：

| 工作台 | 页面文件 | agentType |
|--------|---------|-----------|
| 数据工作台 | `DataWorkbenchLayout.tsx` | `data` |
| 本体工作台 | `BusinessWorkbenchLayout.tsx` | `ontology` |
| 知识工作台 | `KnowledgeView.tsx` | `knowledge` |
| 安全中心 | `SecurityCenter.tsx` | `security` |
| 应用场景 | `ScenarioManagementView.tsx` | `scenario` |

| 步骤 | 交付物 | 工期 |
|------|--------|:--:|
| C1 | `ICopilotAgentService` 接口 + `CopilotAgentServiceImpl` | 1.5d |
| C2 | `POST /api/v1/agent/copilot/chat` 端点 | 0.5d |
| C3 | 前端 `CopilotPanel.tsx` 通用组件 | 1d |
| C4 | 5工作台挂载CopilotPanel | 1d |

---

## 五、Week 4：自进化闭环

### 5.1 五阶段循环演进网络

```
                  ┌── 人工审批门 ──┐
                  ↓                ↑
数据探针诊断 → 语义本体演进 → 知识向量重构 → 安全自愈重校 → 仿真部署发布
     ↑                                                          |
     └──────────────────── 循环 ────────────────────────────────┘
```

**关键约束：Agent生成提案，人审批后执行。**

| 阶段 | 触发条件 | 执行Agent | 产出 | 审批 |
|------|---------|-----------|------|:--:|
| **数据探针诊断** | DQ规则告警 / Pipeline失败 | data-agent | `DiagnosisReport` | 无需审批（只读诊断） |
| **语义本体演进** | 新数据源接入 / 字段变更 | ontology-agent | `OntologyChangeProposal` | ✅ **人工审批**（submit→approve→execute） |
| **知识向量重构** | 本体变更审批通过后 | knowledge-agent | 自动触发`RAGQuery`重新检索 | 无需审批（跟随本体变更） |
| **安全自愈重校** | 权限冲突 / 审计异常 | security-agent | `PolicyAdjustment` | ✅ **人工审批**（安全策略变更必须审批） |
| **仿真部署发布** | 前四阶段产物就绪 | scenario-agent | `DeploymentPlan` | ✅ **人工审批**（GitOps写回须审批） |

**审批流程**：利用已有的 `ecos_agent.agent_approval` 表 + `OntologyProposalController` 的 submit/approve/reject/execute 模式：
1. Agent生成 Proposal → 写入 `agent_approval` (status=PENDING, risk_level)
2. 高风险(risk=HIGH) → 通知管理员 → 人工 approve/reject
3. 低风险(risk=LOW) → 自动批处理 → 但保留人工撤销能力
4. 审批通过 → Agent执行 → 写 `ecos_evolution_log`

### 5.2 闭环实现

利用已有的 `OrchestrationService.plan(Mission)` 定义进化Mission：

```java
Mission mission = new Mission();
mission.setTitle("自治进化-数据质量告警");
mission.setGoal("检测到ecos_finance_invoice表NULL_VIOLATION → 全流程自治修复");
mission.setMode(CollaborationMode.SEQUENTIAL); // 5阶段串行
// OrchestrationService.plan(mission) → 拆解为5个Task → Executor按序执行
```

**AgentMesh协同**：通过 `POST /api/v1/agent-mesh/orchestrate` 端点触发：

```json
{
  "trigger": "DATA_QUALITY_ALERT",
  "context": { "tableName": "ecos_finance_invoice", "anomalyType": "NULL_VIOLATION" },
  "pipeline": ["data-agent", "ontology-agent", "knowledge-agent", "security-agent", "scenario-agent"]
}
```

每个Agent的输出作为下一个Agent的输入，写入 `ecos_evolution_log` 审计表。

### 5.3 新增表

```sql
CREATE TABLE IF NOT EXISTS ecos_agent.ecos_evolution_log (
    id VARCHAR(64) PRIMARY KEY,
    mission_id VARCHAR(64),
    stage VARCHAR(32),      -- DIAGNOSIS/ONTOLOGY_EVOLVE/KNOWLEDGE_REBUILD/SECURITY_HEAL/DEPLOYMENT
    agent_id VARCHAR(64),
    input_context JSONB,
    output_result JSONB,
    status VARCHAR(32),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
```

| 步骤 | 交付物 | 工期 |
|------|--------|:--:|
| E1 | `ecos_evolution_log` 表 + `EvolutionOrchestrator`（agent-service新增） | 1d |
| E2 | `QualityInspectionService` 触发→自动创建Mission→OrchestrationService编排 | 1.5d |
| E3 | 前端 `CognitiveOperatingSystem.tsx` 新增进化循环可视化面板（5阶段环形图+日志时间线） | 1.5d |

---

## 六、交付总览

| 周 | 主题 | 核心交付 | curl验证 |
|:--:|------|------|------|
| W1 | 数据流+Tool对接 | 5层数据分层 + 14个Tool + Memory持久化 | `POST /api/v1/agent/tools/QueryPhysicalTable/execute` → 真实表数据 |
| W2 | 5大Agent | System Prompt + Tool白名单 + 知识绑定 + LLM路由 | `POST /api/v1/agent/copilot/chat` agentId=data → 返回含数据目录引用的回答 |
| W3 | Copilot沙箱 | CopilotAgentService + CopilotPanel + 5工作台挂载 | 在数据工作台右下角"供应商准时率" → 返回含思考链的回答 |
| W4 | 自进化闭环 | EvolutionOrchestrator + 进化面板 + 5阶段流水线 | 触发DQ告警 → 自动走完5阶段 → `ecos_evolution_log` 可查 |

**总工期：4周。** 每阶段结束可独立演示。

---

## 七、关键决策（已确认）

| # | 决策点 | 结论 |
|:--:|--------|------|
| 1 | 数据分层模型 | **5层**：L0源→L1近源→L2加工→L3语义→L4应用。不可变单向流，下层不依赖上层 |
| 2 | Tool覆盖范围 | **20个Tool**，每Tool对应已存在端点。数据7+本体6+知识4+安全3。场景Tool Phase 2封装 |
| 3 | Agent操作审批 | **生成提案等人工审批**。利用已有 `agent_approval` 表+ProposalController的submit/approve/reject流程 |
| 4 | 向量存储 | **跟着三版本走**：standard/enterprise用pgvector，flagship用Doris向量化能力替换pgvector |
