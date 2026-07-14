# ECOS 企业认知操作系统 — 功能需求规格说明书

> 基于 `/mnt/d/workspace/ecos设计/` 下全部设计文档（101-10320 需求文档 + 01-18 设计文档）整理
> 结合当前 c2eos 研发现状对照分析
> 生成时间：2026-06-14

---

## 一、系统概述

**项目名称**：Enterprise Cognitive Operating System (ECOS)，代号 Project Atlas  
**项目定位**：Palantir Foundry + AIP + AgentOS 的企业级认知操作系统  
**核心架构模式**：Ontology Driven + Agent Native + Event Driven  
**架构分层**（7层）：

```
Mission Control（任务指挥中心）
  └─ Cognitive Layer（认知层 — World Model / Causal Graph / Scenario）
       └─ Knowledge Layer（知识层 — Knowledge Graph / Memory / Learning）
            └─ Agent Layer（智能体层 — Agent Runtime / Agent Mesh / Tool）
                 └─ Operations Layer（运营层 — Workflow / Action / Rule）
                      └─ Semantic Layer（语义层 — Ontology / Object Runtime / Query）
                           └─ Data Platform Layer（数据平台层 — Catalog / Pipeline / Storage）
```

**DIKW 映射**：数据层(Data) → 语义层(Info) → 知识层(Knowledge) → 认知层(Wisdom)

---

## 二、20个模块功能需求

### 模块1：数据平台层（101）
> 文档：101-数据平台层功能设计说明书.txt（⚠️ 当前为空文件）
> 覆盖主题：数据接入、数据存储、数据处理、数据目录、数据治理、数据血缘、数据安全

---

### 模块2：数据目录 / Data Catalog（102）

**文档**：102-数据目录.txt（1225行，第2章）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| CAT-001 | 数据资产中心 | 统一管理 Database/Table/View/File/Dataset/API/Stream/Feature/Vector Dataset/Ontology Object/Knowledge Graph 共11种资产类型 |
| CAT-002 | 元数据管理 | 技术元数据（DB/表/字段）+ 业务元数据（名称/定义/Owner）+ 运行元数据，自动采集+在线编辑+版本管理（Draft→Published→Archived） |
| CAT-003 | 业务术语库 | 统一企业业务语言，术语状态机 Draft→Review→Approved→Published→Deprecated |
| CAT-004 | 数据分类分级 | 5级分类（公开→内部→敏感→机密→绝密），Agent自动识别打标签 |
| CAT-005 | 数据发现 | Keyword/Semantic/Graph/Ontology 四种搜索 + AI搜索增强 |
| CAT-006 | 数据血缘 | Table/Column/Object/Knowledge 四级血缘，可视化图表 |
| CAT-007 | 影响分析 | 评估变更影响（数据集/报表/Agent/Workflow/API），风险评分 Low→Medium→High→Critical |
| CAT-008 | 数据所有权 | Data Owner/Steward/Custodian 三角色，每个资产至少1个Owner |
| CAT-009 | 数据评分 | 加权评分：Quality 30% + Popularity 20% + Freshness 20% + Completeness 15% + Governance 15% |
| CAT-010 | 数据订阅 | 支持 Dataset/API/Ontology Object/Knowledge Asset 订阅，邮件/短信/企微通知 |
| CAT-011 | 数据市场 | 企业内部共享，申请流程 发现→申请→审批→授权→使用 |

**当前现状**：✅ 前端已有 DataCatalog 页面（本体元数据真实，实例数据已真实化），DatasetExplorer 血缘 Tab 已真实化。⚠️ 术语库、分类分级、数据市场尚未开始。

---

### 模块3：数据处理与管道（103）

**文档**：103-第3章 数据处理与管道.txt（1357行，第3章 Pipeline Platform）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| PIP-001 | Pipeline Designer | 可视化设计器，左侧节点库+中间画布+右侧属性面板 |
| PIP-002 | Pipeline 生命周期 | Draft→Testing→Published→Running→Completed（异常 Failed→Retry，归档 Archived） |
| PIP-003 | 节点库 | Source(DB/File/Stream/API) + Transform(SQL/Python/Spark/Mapping) + 清洗(去重/空值/格式) + Quality + ML + Agent + Output(DB/DataLake/对象运行时/知识图谱/向量存储) |
| PIP-004 | Pipeline Runtime | Batch/Streaming/Hybrid，自动 DAG，依赖分析+并行执行+失败恢复 |
| PIP-005 | 调度中心 | 手工/Cron/Event/Workflow/Agent 五种触发方式 |
| PIP-006 | 数据产品中心 | 生命周期 Draft→Review→Published→Consumed→Retired |
| PIP-007 | 版本管理 | Git 式版本管理（Draft/Branch/Merge/Release） |
| PIP-008 | Pipeline Marketplace | 沉淀企业最佳实践 Pipeline 模板 |

**数据分层模型**：Raw → Curated → Business → Knowledge Object → Agent Knowledge

**当前现状**：✅ 前端已有 PipelineBuilder 页面，已从 Workflow 分离为独立 Pipeline 实体。⚠️ 节点库仅基础框架，ML/Agent 节点未实现；调度中心未实现；Marketplace 未开始。

---

### 模块4：数据存储与湖仓（104）

**文档**：104 第4章 数据存储与湖仓.txt（1296行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| STO-001 | 对象存储 | S3/MinIO/OSS/COS/OBS，支持最大10GB+，断点续传 |
| STO-002 | Lakehouse 引擎 | Apache Iceberg，MinIO+Iceberg+Spark |
| STO-003 | 数据分层 | Raw→Standard→Business→Knowledge→Ontology 五层 |
| STO-004 | Iceberg 表管理 | 创建/修改Schema/快照/回滚/预览 |
| STO-005 | 数据仓库 | 事实表/维度表/指标表，Star/Snowflake/Data Vault |
| STO-006 | 向量存储 | Milvus/PGVector/Weaviate/Qdrant，HNSW/IVF/Flat 索引 |
| STO-007 | 图存储 | Neo4j/JanusGraph/NebulaGraph |
| STO-008 | 统一数据访问层 | Object/SQL/Graph/Vector 四种统一 API |
| STO-009 | 生命周期管理 | Active→Warm→Cold→Archive→Deleted，按法规保留 |
| STO-010 | 安全 | AES256 静态加密 + TLS1.3 传输加密 + KMS |

**当前现状**：✅ PostgreSQL (sys_man + ecos_knowledge) 作为元数据存储+向量存储。⚠️ Iceberg/Lakehouse、对象存储、图存储均未实现。

---

### 模块5：数据治理与质量（105）

**文档**：105-第5章 数据治理与质量.txt（1233行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| GOV-001 | 数据标准中心 | 数据对象/字段/指标/编码四类标准，生命周期管理 |
| GOV-002 | 数据质量中心 | Dashboard 展示质量评分/异常数量/趋势/分布 |
| GOV-003 | 质量规则引擎 | 字段/表/跨表/业务/AI 五类规则，实时/批量/事件驱动 |
| GOV-004 | 质量问题管理 | Issue 状态机 New→Assigned→In Progress→Resolved→Closed |
| GOV-005 | 主数据管理(MDM) | Customer/Supplier/Product/Material 等，Golden Record 模式 |
| GOV-006 | 合规治理 | GDPR/CCPA/数据安全法/网安法/个保法覆盖 |
| GOV-007 | Agent 治理助手 | 自动发现异常/推荐规则/分类敏感数据 |

**当前现状**：❌ 后端 Governance API 存在但为空（空 Controller），前端无对应页面。

---

### 模块6：Ontology 平台（1026）

**文档**：1026-第6章 ontoloty平台.txt（1175行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| ONT-001 | Domain 管理 | Sales/SupplyChain/Finance/HR 等业务域，Draft→Published→Deprecated |
| ONT-002 | Entity Designer | 4种分类 Master/Transaction/Event/Reference Entity |
| ONT-003 | Property Designer | String/Number/Boolean/Date/Enum/Reference/JSON，含校验规则 |
| ONT-004 | Relationship Designer | OneToOne/OneToMany/ManyToMany |
| ONT-005 | Action Designer | Approve/Reject/Suspend/Activate，ECOS 区别于传统 MDM 的核心 |
| ONT-006 | Rule Designer | Validation/Calculation/Decision/Agent 四类规则 |
| ONT-007 | Ontology Explorer | 可视化浏览实体关系，支持缩放/展开/路径分析 |
| ONT-008 | 版本管理 | Major.Minor.Patch，Draft→Review→Published→Deprecated→Archived |
| ONT-009 | Agent Assistant | 自动生成实体/推荐关系/生成规则 |

**当前现状**：✅ 前端 /api/ontology 返回真实实体数据（从 ecos_object_data 读取，8个Customer/4个Supplier等）。⚠️ Entity Designer/Property Designer/Action Designer 未实现可视化编辑器。

---

### 模块7：对象运行时 / Object Runtime（1027）

**文档**：1027第7章 对象运行时.txt（1266行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| OBJ-001 | Object Explorer | 统一浏览 Customer/Supplier/Product/Asset/Facility 等 |
| OBJ-002 | Object Detail | Summary→Properties→Relationships→Actions→Timeline→Files→Workflow→Knowledge |
| OBJ-003 | 对象创建 | 提交→校验→生成ID→创建成功→触发事件 |
| OBJ-004 | 状态机 | Draft→Submitted→Approved→Active→Suspended→Archived |
| OBJ-005 | 查询引擎 | Attribute/Relationship/Semantic 三种查询 |
| OBJ-006 | 关系引擎 | 图存储对象关系，GraphQL/ObjectQL |
| OBJ-007 | Timeline | 记录创建/修改/审批/Workflow/Agent 操作 |
| OBJ-008 | 版本管理 | 查看/比较/回滚 |
| OBJ-009 | Action Runtime | 执行 Ontology 定义的 Action |
| OBJ-010 | Event Bus | ObjectCreated/ObjectUpdated/ActionExecuted 等事件 |
| OBJ-011 | 权限控制 | Object/Property/Action/Relationship 四级控制 |
| OBJ-012 | 评分系统 | 0-100 信用/风险评分 |

**当前现状**：⚠️ 前端数据浏览已真实化（对象查询工具 `object_query` 接真实数据）。对象创建/详情/状态机/Event Bus 部分实现。

---

### 模块8：动作与规则引擎（1028）

**文档**：1028第8章动作与规则引擎.txt（1255行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| ACT-001 | Action Runtime | User→Action→Rule→Policy→StateMachine→Event |
| ACT-002 | Action 分类 | State Action / Data Action / Workflow Action / Agent Action / Integration Action |
| ACT-003 | Rule Engine | 4种表达式 SQL/DSL/JSON/Agent，Validation/Calculation/Decision/Event |
| ACT-004 | Policy Engine | 策略定义+评估+执行 |
| ACT-005 | Action 审计 | 全链路 Trace |

**当前现状**：⚠️ PolicyEngine 为桩代码。Rule Engine 未独立实现。

---

### 模块9：工作流与流程自动化（1029）

**文档**：1029第9章工作流与流程自动化.txt（1350行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| WF-001 | Workflow Designer | 可视化流程设计器，BPMN 2.0 风格 |
| WF-002 | Workflow Engine | 状态机驱动，支持并行/串行/条件/子流程 |
| WF-003 | 工作流实例 | CREATED→RUNNING→PAUSED→COMPLETED/FAILED/CANCELLED |
| WF-004 | 任务管理 | PENDING→ASSIGNED→COMPLETED→REJECTED |
| WF-005 | 流程监控 | 实时查看/干预/重新分配 |
| WF-006 | Agent Node | Workflow 中嵌入 Agent 节点（input/output mapping + decision routing） |

**当前现状**：✅ ECOS Workflow Engine 已实现（state machine 驱动 + Agent Node 嵌入）。✅ 前端 workflow_start 工具可用。⚠️ 可视化 Workflow Designer 未实现。

---

### 模块10：统一查询与语义层（10210）

**文档**：10210第10章 统一查询与语义层.txt（1292行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| QRY-001 | Semantic Query Engine | ObjectQL 查询语言 |
| QRY-002 | GraphQL API | 自动从 Ontology 生成 |
| QRY-003 | Search | Keyword/Semantic/Graph/Agent 四种搜索 |
| QRY-004 | Metric Engine | 统一指标计算引擎 |

**当前现状**：⚠️ 后端有 GraphQL schema 基础，ObjectQL 未实现。搜索依赖 Agent 工具链。

---

### 模块11：智能体运行时 / Agent Runtime（10211）

**文档**：10211第11章智能体运行时.txt（1166行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| AGT-001 | Agent Builder | 创建/配置 Agent（Goal/Tools/Prompts/Constraints） |
| AGT-002 | Tool Registry | 注册/发现/调用 Tool（Object/Workflow/API/Knowledge/SQL/Agent 6种类型） |
| AGT-003 | Prompt Registry | 模板管理+版本管理 |
| AGT-004 | Agent Runtime | Planner→Task Decomposer→Tool Selector→Executor→Evaluator→Reflection |
| AGT-005 | Memory Architecture | Working→Session→Long-Term→Enterprise 四层记忆 |
| AGT-006 | Human Approval | 触发条件（金额/删除/外部API），WAITING_APPROVAL 状态 |
| AGT-007 | Agent Governance | 权限/预算/成本/风险策略 |
| AGT-008 | Telemetry | Prompt/Tool Call/Latency/Cost/Token Usage |

**当前现状**：✅ Agent Runtime + ReAct 引擎已实现（4工具：object_query/ontology_explore/knowledge_search/workflow_start）。✅ 三层记忆体系（工作/短期/长期）。✅ Agent Studio 页面已真实化。⚠️ Agent Builder 未实现可视化创建；Human Approval 未实现；预算控制未实现。

---

### 模块12：智能体网络 / Agent Mesh（10212）

**文档**：10212 第12章智能体网络.txt（1474行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| MSH-001 | Agent Registry | Agent 发现+能力注册+版本管理 |
| MSH-002 | Mission Runtime | Mission=Goal+Plan+Tasks+Agents+Result |
| MSH-003 | Coordinator Agent | Mesh 大脑，任务拆解+Agent 选择+执行协调 |
| MSH-004 | A2A Protocol | Agent-to-Agent 通信标准（REQUEST/RESPONSE/EVENT/BROADCAST/NEGOTIATION） |
| MSH-005 | 协作模式 | Supervisor/Swarm/Pipeline/Debate 四种模式 |
| MSH-006 | Agent Marketplace | 管理 Agent/Tool/Prompt/Workflow |
| MSH-007 | Agent Reputation | 加权评分 Accuracy 40%+Reliability 30%+Cost 20%+Latency 10% |
| MSH-008 | Shared Memory Graph | Agent→Memory Graph→Knowledge Graph→Ontology |

**当前现状**：❌ 未开始。A2A Protocol、Multi-Agent 协作、Marketplace 均待实现。

---

### 模块13：知识图谱平台（10213）

**文档**：10213第13章知识图谱平台.txt（1250行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| KNG-001 | Knowledge Graph Builder | 创建/管理知识图谱 |
| KNG-002 | 实体/关系抽取 | Agent 自动抽取 |
| KNG-003 | 知识推理 | 规则推理+图推理 |
| KNG-004 | Knowledge Explorer | 可视化浏览/搜索 |
| KNG-005 | 知识关联 | 对象↔知识↔文档↔Agent Result |

**当前现状**：⚠️ 知识库搜索（KnowledgeSearchTool）已实现 BGE 语义向量检索。知识图谱图结构（Neo4j/Cypher）未实现。Knowledge Explorer 未实现。

---

### 模块14：世界模型平台（10214）

**文档**：10214世界模型平台.txt（1199行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| WLD-001 | World Model Builder | Causal Graph 因果图构建 |
| WLD-002 | Scenario Simulation | 情景模拟（What-If 分析） |
| WLD-003 | Optimization Engine | 优化求解引擎 |
| WLD-004 | Strategic Planning | 战略规划支持 |

**当前现状**：⚠️ 后端 Goals/CausalLinks/Scenarios API 存在但返回空数组。前端 CognitiveOperatingSystem 页面已真实化。World Model 引擎未实现。

---

### 模块15：任务指挥中心（10215）

**文档**：10215任务指挥中心.txt（487行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| MSC-001 | Enterprise Dashboard | 全局仪表盘 |
| MSC-002 | Mission Monitoring | Agent/Workflow 实时监控 |
| MSC-003 | Event Center | 企业事件中心 |
| MSC-004 | Alert & Notification | 告警+通知 |

**当前现状**：✅ 前端 MonitoringCenter 页面已真实化。⚠️ Event Center 和 Dashboard 部分 mock。

---

### 模块16：自治企业引擎（10216）

**文档**：10216第16章自治企业引擎.txt（388行，较短）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| AUT-001 | Autonomous Decision | 自治决策引擎 |
| AUT-002 | Learning Loop | 决策→执行→评估→学习的闭环 |

**当前现状**：❌ 未开始。

---

### 模块17：应用开发平台（10317）

**文档**：10317第17章应用开发平台.txt（858行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| APP-001 | Low-Code Builder | 低代码应用构建器 |
| APP-002 | App Marketplace | 应用市场 |
| APP-003 | API Builder | API 快速构建 |

**当前现状**：❌ 未开始。

---

### 模块18：企业数据编制平台（10318）

**文档**：10318企业数据编制平台.txt（141行，极短）

**当前现状**：❌ 未开始。

---

### 模块19：治理与安全中心（10319）

**文档**：10319第19章 治理与安全中心.txt（1287行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| SEC-001 | Identity & Access | OAuth2/OIDC/LDAP/SAML/MFA |
| SEC-002 | Permission | RBAC/ABAC/PBAC |
| SEC-003 | Security Monitoring | 安全监控+告警 |
| SEC-004 | Compliance | 合规报告 |

**当前现状**：⚠️ JWT 认证已实现。ABAC 框架存在（abac 包+PolicyEngine 桩代码）。RBAC 角色/权限管理部分实现。

---

### 模块20：统一门户与用户体验平台（10320）

**文档**：10320第20章统一门户与用户体验平台.txt（888行）

**功能需求**：

| ID | 功能 | 说明 |
|----|------|------|
| UX-001 | Unified Portal | 统一登录门户 |
| UX-002 | Personal Workspace | 个人工作台 |
| UX-003 | Global Search | 全局搜索 |
| UX-004 | Design System | 统一设计规范 |

**当前现状**：✅ 前端登录页面已实现。⚠️ 个人工作台和全局搜索未实现。

---

## 三、与当前研发现状对照

### 已完成 ✅

| 模块 | 完成度 | 详情 |
|------|--------|------|
| Agent Runtime（核心） | 80% | ReAct 引擎 + 4 工具 + 三层记忆 |
| Agent Studio 前端 | 90% | 真实化完成，工具列表/配置展示 |
| Data Catalog 基础 | 60% | 数据浏览+血缘已真实化，术语库/市场未开始 |
| Workflow Engine | 60% | 状态机+Agent Node，可视化 Designer 未实现 |
| Ontology 基础 | 50% | /api/ontology 返回真实数据，Designer 未实现 |
| Knowledge Search | 70% | BGE 向量搜索完成，图谱存储未实现 |
| Pipeline Builder | 40% | 已从 Workflow 分离，节点库仅基础 |

### 进行中 ⚠️

| 模块 | 当前状态 |
|------|---------|
| Object Runtime | 对象查询+浏览真实化，创建/详情/状态机/EventBus 部分实现 |
| Cognitive Operating System | 前端真实化，后端空 API |
| Agent Mesh | 未开始 |
| World Model | 前端真实化，后端空 API |
| Governance Center | 后端空 Controller |

### 未开始 ❌

| 模块 |
|------|
| Lakehouse/对象存储/图存储 |
| MDM 主数据管理 |
| Agent Marketplace/Reputation |
| Low-Code Builder |
| 自治企业引擎 |
| 统一门户（工作台/全局搜索） |

---

## 四、MVP 优先级路线图

### P0 — 核心闭环（当前阶段，预计2-3个月）

| 优先级 | 模块 | 目标 |
|--------|------|------|
| P0-1 | Agent Runtime 完善 | Agent Builder 可视化 + Human Approval + 完整 Memory Architecture |
| P0-2 | Object Runtime 完善 | 对象创建/详情/状态机全链路 |
| P0-3 | Workflow Designer | 可视化设计器，BPMN 2.0 风格 |
| P0-4 | Data Catalog 完善 | 术语库 + 分类分级（Agent 自动） |
| P0-5 | 治理中心 MVP | 数据质量 Dashboard + 规则引擎 |
| P0-6 | World Model 后端 | Goals/CausalLinks/Scenarios 真实实现 |

### P1 — 能力扩展（预计3-6个月）

| 优先级 | 模块 | 目标 |
|--------|------|------|
| P1-1 | Agent Mesh MVP | Supervisor 模式 + A2A Protocol 基础 |
| P1-2 | Knowledge Graph | Neo4j 图存储 + Explorer |
| P1-3 | Pipeline 节点库扩展 | ML Agent 节点 |
| P1-4 | Lakehouse 基础 | Iceberg + MinIO |

### P2 — 企业级完善（预计6-12个月）

| 优先级 | 模块 | 目标 |
|--------|------|------|
| P2-1 | Agent Marketplace | Tool/Prompt/Agent 市场 |
| P2-2 | 自治企业引擎 | 决策→执行→学习闭环 |
| P2-3 | Low-Code Builder | 应用快速构建 |
| P2-4 | 统一门户 | 工作台 + 全局搜索 |

---

## 五、关键技术决策汇总

| # | 决策 | 说明 |
|---|------|------|
| 1 | Ontology Driven | 所有业务对象源自 Ontology 定义，是 ECOS 灵魂 |
| 2 | AI/Agent Native | 每个模块原生支持 Agent 调用和语义检索 |
| 3 | Event Driven | Kafka 统一事件总线，Saga+Outbox 解决分布式事务 |
| 4 | Database Per Service | 10个独立 Schema，禁止跨服务写库 |
| 5 | Polyglot Persistence | PG(事务)+ClickHouse(分析)+Neo4j(图)+Redis(缓存)+ES(搜索)+VectorDB(向量) |
| 6 | 四层记忆架构 | Working→Session→Long-Term→Enterprise Memory |
| 7 | BGE 语义检索 | bge-small-zh-v1.5 (512维)，Python 微服务，PG 余弦排序 |
| 8 | Java 21 + Spring Boot 3 | 主力后端技术栈 |
| 9 | React 19 + Vite 6 | 前端技术栈 |
| 10 | DeepSeek v4-flash | Agent LLM 引擎 |
| 11 | A2A Protocol | Agent 间通信标准 |
| 12 | Human Approval | 企业级关键能力（金额>100万/删除/外部API需审批） |
| 13 | 绞杀榕迁移 | DataBridge v2 逐步迁移到 ECOS 架构 |

---

## 六、文档清单

### 需求文档（功能需求）

| 编号 | 文件名 | 行数 | 内容 |
|------|--------|------|------|
| - | 101-数据平台层功能设计说明书.txt | 0 | ⚠️ 空文件 |
| 2 | 102-数据目录.txt | 1225 | 数据资产中心/元数据/术语库/分类分级/血缘/市场 |
| 3 | 103-第3章 数据处理与管道.txt | 1357 | Pipeline Designer/节点库/DAG引擎/调度/数据产品 |
| 4 | 104 第4章 数据存储与湖仓.txt | 1296 | 对象存储/Lakehouse/向量/图/统一访问层 |
| 5 | 105-第5章 数据治理与质量.txt | 1233 | 数据标准/质量/MDM/合规/Agent治理助手 |
| 6 | 1026-第6章 ontoloty平台.txt | 1175 | Domain/Entity/Property/Relationship/Action Designer |
| 7 | 1027第7章 对象运行时.txt | 1266 | Object Explorer/Detail/状态机/查询引擎/Timeline |
| 8 | 1028第8章动作与规则引擎.txt | 1255 | Action Runtime/Rule Engine/Policy Engine |
| 9 | 1029第9章工作流与流程自动化.txt | 1350 | Workflow Designer/Engine/任务管理/Agent Node |
| 10 | 10210第10章 统一查询与语义层.txt | 1292 | ObjectQL/GraphQL/Semantic Query Engine |
| 11 | 10211第11章智能体运行时.txt | 1166 | Agent Builder/Tool Registry/记忆/Memory/Governance |
| 12 | 10212 第12章智能体网络.txt | 1474 | Agent Registry/A2A Protocol/协作模式/Marketplace |
| 13 | 10213第13章知识图谱平台.txt | 1250 | Knowledge Graph Builder/抽取/推理/Explorer |
| 14 | 10214世界模型平台.txt | 1199 | Causal Graph/Scenario Simulation/Optimization |
| 15 | 10215任务指挥中心.txt | 487 | Dashboard/Monitoring/Event Center |
| 16 | 10216第16章自治企业引擎.txt | 388 | Autonomous Decision/Learning Loop |
| 17 | 10317第17章应用开发平台.txt | 858 | Low-Code Builder/App Marketplace |
| 18 | 10318企业数据编制平台.txt | 141 | (极短) |
| 19 | 10319第19章 治理与安全中心.txt | 1287 | Identity/Permission/Security/Compliance |
| 20 | 10320第20章统一门户与用户体验平台.txt | 888 | Portal/Workspace/Global Search/Design System |

### 设计文档（架构与规范）

| 编号 | 文件名 | 行数 | 内容 |
|------|--------|------|------|
| 01 | 01-项目章程.txt | 635 | 项目章程+角色定义+SRS起步 |
| 02 | 02-需求规格.txt | 995 | SRS v1.0正式版，FR-100~FR-704，七层架构 |
| 03 | 03-HLD总体设计说明书.txt | 984 | C4架构图，4大原则，8个Context，29个微服务 |
| 04 | 04-DDD领域模型设计说明书.txt | 1065 | 8个Bounded Context，65个Aggregate，180个Domain Event |
| 05 | 05-微服务拆分设计说明书.txt | 1240 | 29个微服务，12个仓库，Database Per Service |
| 06 | 06-数据库设计说明书.txt | 1192 | 220张表，Polyglot Persistence，EAV模型 |
| 07 | 07-Ontology元模型设计说明书.txt | 992 | DSL→Compiler→自动生成，23个标准实体 |
| 08 | 08-Agent Runtime设计说明书.txt | 1028 | Agent Runtime，四层记忆，4种Multi-Agent模式 |
| 09 | 09-Agent Mesh设计说明书.txt | 996 | A2A Protocol，4种协作模式，Agent Marketplace |
| 10 | 10-Knowledge Graph设计说明书.txt | 1207 | Enterprise Memory Graph，知识生命周期 |
| 11 | 11-World Model设计说明书.txt | 1082 | Causal Graph/Bayesian/System Dynamics三种模型 |
| 12 | 12-前端设计系统规范.txt | 1370 | Next.js 15，原子设计，Design Tokens |
| 13 | 13-API设计规范.txt | 1220 | REST+GraphQL+WebSocket，统一错误码 |
| 14 | 14-安全架构设计说明书.txt | 1070 | OAuth2/OIDC，RBAC/ABAC/PBAC，KMS |
| 15 | 15-部署架构设计说明书.txt | 1088 | Kubernetes，10个Namespace，Blue-Green |
| 16 | 16-DevOps规范.txt | 1400 | CI/CD，GitOps，Monitoring |
| 17 | 17-测试规范.txt | 820 | Unit/Integration/E2E/Performance，Chaos Engineering |
| 18 | 18-MVP实施路线图.txt | 1040 | 12~18个月，40~80人，3个Phase |
