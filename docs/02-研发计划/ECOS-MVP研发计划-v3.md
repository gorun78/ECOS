# ECOS MVP 补齐研发计划 v3

> 日期：2026-06-14  
> 基于：ECOS-功能需求规格说明书 + 当前研发现状 + 设计文档全量分析  
> 原则：**先补核心闭环 → 再扩能力边界 → 最后体验打磨**

---

## 一、当前真实基线（对比旧计划）

旧计划（v1/v2）假设了大量"待做"工作，实际已有进展：

| 模块 | 旧计划认为 | 实际情况 |
|------|-----------|---------|
| Agent Runtime | ❌ 全模拟 | ✅ ReAct引擎 + 4工具 + 三层记忆 + AgentNode嵌入Workflow |
| Knowledge Search | ❌ MySQL LIKE | ✅ BGE语义向量检索(bge-small-zh-v1.5, 512维) |
| Workflow Engine | ❌ 需新建 | ✅ 状态机驱动 + Agent Node |
| Ontology 基础 | ⚠️ 部分 | ✅ /api/ontology返回真实数据 |
| Data Catalog | ❌ 未开始 | ✅ 数据浏览+血缘已真实化 |
| Pipeline | ❌ 未开始 | ✅ 已从Workflow分离为独立实体 |
| 前端 c2eos | ❌ 未开始 | ✅ 11页面，AgentStudio/COG/Monitoring真实化 |
| PostgreSQL | ❌ 需迁移 | ✅ sys_man + ecos_knowledge双库，PG向量搜索 |

**结论**：知识层和智能化层的MVP核心已基本完成（Agent + Knowledge + Workflow），当前瓶颈在**数据层前端真实化**和**决策层后端真实化**。

---

## 二、补齐范围（基于设计文档差距分析）

### P0 — 核心闭环补齐（2-3个月）

| 优先级 | 模块 | 差距 | 工作量 |
|--------|------|------|--------|
| **P0-1** | Object Runtime 全链路 | 对象创建/详情/状态机/EventBus 部分实现，需补全 | 2周 |
| **P0-2** | Ontology Designer | 可视化实体/属性/关系/Action设计器 | 3周 |
| **P0-3** | Workflow Designer | BPMN可视化设计器 | 2周 |
| **P0-4** | World Model 后端 | Goals/CausalLinks/Scenarios 真实实现 | 2周 |
| **P0-5** | Data Quality Dashboard | 质量规则引擎 + 问题管理 | 2周 |
| **P0-6** | IAM 完善 | SSO/OAuth2 + 数据权限过滤 + 操作审计 | 1周 |

### P1 — 能力扩展（3-6个月）

| 优先级 | 模块 | 差距 | 工作量 |
|--------|------|------|--------|
| **P1-1** | Agent Builder | 可视化创建/配置/测试Agent | 3周 |
| **P1-2** | Knowledge Graph (Neo4j) | 图存储 + 实体关系抽取 + Explorer | 3周 |
| **P1-3** | Pipeline 节点库 | ML节点 + Agent节点 + 调度中心 | 2周 |
| **P1-4** | Agent Mesh MVP | Supervisor模式 + A2A Protocol基础 | 3周 |
| **P1-5** | Data Catalog 完善 | 术语库 + 分类分级(Agent自动) + 数据市场 | 2周 |
| **P1-6** | 统一查询层 | ObjectQL + GraphQL自动生成 | 2周 |

### P2 详细任务拆解（概览级，执行前再细化）

---

#### P2-1：Lakehouse (Iceberg + MinIO) [4周]

> 关联设计：104 第4章 数据存储与湖仓

| # | 任务 | 内容 |
|---|------|------|
| 1.1 | MinIO对象存储部署 | Docker部署，S3兼容API接入 |
| 1.2 | Apache Iceberg表格式 | Iceberg Catalog配置，表创建/演化 |
| 1.3 | 湖仓数据摄入 | Pipeline节点→写入Iceberg表 |
| 1.4 | 查询引擎对接 | Trino/Spark SQL对接Iceberg→ObjectQL可查 |
| 1.5 | 存储生命周期管理 | 冷热分层，归档策略 |

**验收标准**：Pipeline写入→Iceberg表可见→ObjectQL可查→MinIO存储实际文件。

---

#### P2-2：MDM 主数据管理 [3周]

| # | 任务 | 内容 |
|---|------|------|
| 2.1 | 主数据建模 | 黄金记录定义，匹配规则配置 |
| 2.2 | 数据去重合并 | 精确匹配+模糊匹配+Agent辅助匹配合并 |
| 2.3 | 主数据审批工作流 | 新增/变更→审批→发布 |
| 2.4 | 主数据分发 | 变更事件→下游系统同步 |

**验收标准**：Supplier主数据去重合并→审批→分发到多个下游系统。

---

#### P2-3：Agent Marketplace [3周]

> 关联设计：10211 §11.35 Agent Marketplace

| # | 任务 | 内容 |
|---|------|------|
| 3.1 | Agent上架/下架 | 发布Agent到市场，版本管理 |
| 3.2 | 市场浏览 | 分类/搜索/评分/热门排序 |
| 3.3 | Agent订阅 | 一键导入到我的Agent列表 |
| 3.4 | 评价与反馈 | 星评+文本评价+使用统计 |

**验收标准**：Agent发布→市场可见→其他用户订阅使用→评价反馈闭环。

---

#### P2-4：Agent Reputation 体系 [2周]

| # | 任务 | 内容 |
|---|------|------|
| 4.1 | 信誉指标采集 | 成功率/响应时间/Token消耗/用户评分 |
| 4.2 | 信誉评分计算 | 加权算法，衰减机制 |
| 4.3 | Reputation Dashboard | 信誉排行榜+趋势图 |
| 4.4 | 信誉驱动的Agent选择 | Agent Mesh分派时优先高信誉Agent |

**验收标准**：Agent执行后指标更新→Dashboard可见→Mesh分派时信誉影响选择。

---

#### P2-5：自治企业引擎 [4周]

> 关联设计：10216 第16章 自治企业引擎

| # | 任务 | 内容 |
|---|------|------|
| 5.1 | Goal定义与管理 | 创建企业目标（KPI/OKR），关联World Model |
| 5.2 | 事件感知 | 数据变更/异常/阈值→触发Goal评估 |
| 5.3 | 自主决策链 | 感知→分析→决策（调用Agent）→执行（调用Workflow） |
| 5.4 | 效果反馈闭环 | 决策执行→结果评估→调整策略 |

**验收标准**：定义Goal（供应商质量>90）→数据异常触发→Agent分析→自动发起整改Workflow→结果反馈。

---

#### P2-6：Low-Code Builder [6周]

> 关联设计：10317 第17章 应用开发平台

| # | 任务 | 内容 |
|---|------|------|
| 6.1 | 页面设计器 | 拖拽布局，组件库（表格/表单/图表/地图） |
| 6.2 | 数据绑定 | 拖拽字段绑定Object/API |
| 6.3 | Action编排 | 按钮→触发Workflow/Agent |
| 6.4 | 应用发布 | 一键发布→生成独立应用URL |
| 6.5 | 模板市场 | 预置模板（供应商管理/合规审查/风险管理） |

**验收标准**：拖拽创建供应商管理应用（表格+表单+审批按钮）→发布→生产可用。

---

#### P2-7：统一门户 [2周]

> 关联设计：10320 第20章 统一门户与用户体验平台

| # | 任务 | 内容 |
|---|------|------|
| 7.1 | 工作台首页 | 全局搜索+快捷入口+待办+通知+最近访问 |
| 7.2 | 全局搜索 | 跨模块搜索（资产/Agent/Object/术语） |
| 7.3 | 个性化配置 | 收藏夹/快捷方式/主题偏好持久化 |
| 7.4 | 移动端适配 | 响应式布局核心页面 |

**验收标准**：工作台可搜索任意实体→直接跳转→待办事项可见→移动端可浏览。

---

### P2 汇总

| 阶段 | 内容 | 合计 |
|------|------|------|
| P2-1 | Lakehouse (Iceberg + MinIO) | 20d |
| P2-2 | MDM 主数据管理 | 15d |
| P2-3 | Agent Marketplace | 15d |
| P2-4 | Agent Reputation 体系 | 10d |
| P2-5 | 自治企业引擎 | 20d |
| P2-6 | Low-Code Builder | 30d |
| P2-7 | 统一门户 | 10d |
| **合计** | | **120d（~24周）** |
---

## 三、P0 详细任务拆解

### P0-1：Object Runtime 全链路 [2周]

> 目标：从"能查对象"升级为"完整CRUD + 状态机 + 关系浏览"

| # | 任务 | 前端 | 后端 | 关联设计 |
|---|------|------|------|---------|
| 1.1 | Object 创建表单 | 动态表单（根据Ontology属性定义生成） | POST /api/ecos/objects/{entityCode} | 1027 §7.6 |
| 1.2 | Object 详情页 | Summary+Properties+Relationships+Timeline+Workflow 多Tab | GET /api/ecos/objects/{entityCode}/{id} | 1027 §7.5 |
| 1.3 | Object 状态机 | 状态流转按钮（Draft→Active→Archived） | 状态机引擎 + 状态变更事件 | 1027 §7.7 |
| 1.4 | Object 关系浏览 | 关系图谱（React Flow） | GET /api/ecos/objects/{entityCode}/{id}/relations | 1027 §7.9-7.10 |
| 1.5 | Object Timeline | 时间线组件 | 操作日志聚合查询 | 1027 §7.11 |
| 1.6 | Object 搜索 | 关键词 + 语义搜索 | Elasticsearch 或多字段 LIKE | 1027 §7.18 |

**验收标准**：Customer对象可完整CRUD，状态可流转，关系可浏览，操作可追溯。

**关键文件**：新建/扩展 `ObjectController.java`, `ObjectService.java`, 前端 `ObjectExplorer.tsx` 重写

---

### P0-2：Ontology Designer [3周]

> 目标：从"只读本体"升级为"可视化本体建模工具"

| # | 任务 | 前端 | 后端 | 关联设计 |
|---|------|------|------|---------|
| 2.1 | Domain 管理 | Domain CRUD + 列表 | POST/GET/PUT/DELETE /api/ecos/ontology/domains | 1026 §6.3 |
| 2.2 | Entity Designer | 实体创建/编辑表单 + 属性列表 | POST/GET/PUT/DELETE /api/ecos/ontology/entities | 1026 §6.4 |
| 2.3 | Property Designer | 属性面板（类型/校验/默认值） | POST/GET/PUT/DELETE /api/ecos/ontology/entities/{id}/properties | 1026 §6.5 |
| 2.4 | Relationship Designer | 关系画布（React Flow 拖拽连线） | POST/GET/PUT/DELETE /api/ecos/ontology/relationships | 1026 §6.6 |
| 2.5 | Action Designer | Action定义（类型/规则/策略） | POST/GET Action API | 1026 §6.7-6.8 |
| 2.6 | Rule Designer | 四类规则编辑器 | Rule CRUD + 验证 | 1026 §6.9-6.10 |
| 2.7 | Ontology Explorer | 实体关系图谱 + 缩放/展开 | 已有 /api/ontology 扩展 | 1026 §6.14-6.15 |
| 2.8 | 版本管理 | 发布/回滚操作 | 版本状态机 + Schema同步 | 1026 §6.11-6.12 |

**验收标准**：可创建Domain→定义Entity→添加Property→建立Relationship→发布生效→Object Runtime自动同步。

**关键新建**：
```
后端（~15文件）：
  OntologyController.java, OntologyEntityController.java
  OntologyPropertyController.java, OntologyRelationshipController.java
  OntologyActionController.java, OntologyRuleController.java
  OntologyService.java + impl, 各Entity/Repository/DAO

前端（~8页面/组件）：
  DomainManager.tsx, EntityDesigner.tsx, PropertyEditor.tsx
  RelationshipCanvas.tsx, ActionDesigner.tsx, RuleEditor.tsx
  OntologyExplorer.tsx (增强), OntologyVersionManager.tsx
```

---

### P0-3：Workflow Designer [2周]

> 目标：从"代码定义流程"升级为"可视化拖拽设计器"

| # | 任务 | 前端 | 后端 | 关联设计 |
|---|------|------|------|---------|
| 3.1 | 流程画布 | React Flow 拖拽设计器（节点库+连线+属性面板） | 保存/加载 WorkflowDefinition JSON | 1029 §9.2 |
| 3.2 | 节点库 | 5种节点：开始/结束/人工任务/Agent任务/条件网关 | 节点类型注册机制 | 1029 §9.3 |
| 3.3 | Agent节点配置 | Agent选择+input mapping+output mapping | 对接已有 Agent Runtime | 1029 §9.6 |
| 3.4 | 条件网关 | 条件表达式编辑器 | 条件评估引擎 | 1029 §9.4 |
| 3.5 | 并行分支 | AND-split / AND-join | 已有引擎扩展 | 1029 §9.5 |
| 3.6 | 流程测试 | 模拟运行 + 步骤可视化 | 调试模式 API | 1029 §9.7 |

**验收标准**：拖拽设计供应商准入流程（人工审批→Agent分析→条件分支→自动决策），可发布并真实执行。

---

### P0-4：World Model 后端 [2周]

> 目标：从"空数组API"升级为"可用的因果推理 + 情景模拟"

| # | 任务 | 内容 | 关联设计 |
|---|------|------|---------|
| 4.1 | Goals 真实实现 | 目标创建/层级/进度跟踪，对接 Mission Context 的 GoalAggregate | 10214 §WLD-001 |
| 4.2 | CausalLinks 真实实现 | 因果链创建（A→B），构建因果图 | 10214 §WLD-001 |
| 4.3 | Scenarios 真实实现 | 情景创建/参数配置/模拟执行 | 10214 §WLD-002 |
| 4.4 | What-If 分析 | 修改参数→重算结果→对比差异 | 10214 §WLD-002 |
| 4.5 | World Model 可视化 | 因果图 + 情景对比图表 | 10214 §WLD-003 |

**验收标准**：定义 Goal（提升供应商质量）→建立 CausalLink（严格准入→质量提升）→创建 Scenario（收紧标准 vs 放宽标准）→对比结果。

---

### P0-5：Data Quality Dashboard [2周]

> 目标：从"空API"升级为"可用的质量监控中心"

| # | 任务 | 内容 | 关联设计 |
|---|------|------|---------|
| 5.1 | 质量规则定义 | 5类规则（字段/表/跨表/业务/AI），CRUD | 105 §5.4 |
| 5.2 | 规则执行引擎 | 实时/批量/事件驱动三种模式 | 105 §5.4 |
| 5.3 | 质量评分计算 | Completeness+Accuracy+Consistency+Uniqueness+Timeliness | 105 §5.3 |
| 5.4 | 问题管理 | Issue状态机（New→Assigned→Resolved→Closed） | 105 §5.5 |
| 5.5 | 质量 Dashboard | 评分/异常/趋势/分布四面板 | 105 §5.3 |

**验收标准**：定义5条质量规则→对现有Customer/Supplier数据执行→Dashboard显示评分→异常数据生成Issue。

---

### P0-6：IAM 完善 [1周]

> 目标：从"admin/admin123硬编码"升级为"可生产使用的认证授权"

| # | 任务 | 内容 |
|---|------|------|
| 6.1 | JWT Token 正式签发 | 替换硬编码，登录成功返回 access_token + refresh_token |
| 6.2 | Token 刷新 | refresh_token 换新 access_token |
| 6.3 | 数据权限过滤 | 查询时根据用户角色过滤可见数据（DataPermissionInterceptor） |
| 6.4 | 操作审计 | 所有 CUD 操作写入 ecos_audit_log |
| 6.5 | 前端路由守卫 | 未登录跳转 /login，无权限隐藏菜单 |

---

### P1 详细任务拆解

> 注：P1-3 Pipeline节点库 ✅、P1-4 Agent Mesh ✅ 已完成，仅保留摘要。

---

#### P1-1：Agent Builder [3周]

> 目标：从"代码创建Agent"升级为"可视化Agent配置+测试平台"
> 关联设计：10211 §11.5 Agent Builder, §11.27 Benchmark Center

| # | 任务 | 前端 | 后端 | 关联设计 |
|---|------|------|------|---------|
| 1.1 | Agent配置表单 | Model选择/Prompt编辑器/Tool多选/Knowledge绑定/Memory策略 | PUT /api/agent/config | §11.5 |
| 1.2 | Tool绑定面板 | 可视化工具选择器（搜索/分类/拖拽排序） | GET /api/agent/tools, PUT /api/agent/{id}/tools | §11.11 |
| 1.3 | Knowledge绑定 | 知识库选择+测试查询 | PUT /api/agent/{id}/knowledge | §11.36 |
| 1.4 | Prompt版本管理 | 历史版本/对比/回滚 | Prompt版本CRUD | §11.15 |
| 1.5 | Agent测试控制台 | 单轮/多轮对话测试面板，显示工具调用trace | POST /api/agent/{id}/test | §11.27 |
| 1.6 | Agent列表+详情 | 卡片视图+搜索过滤+状态指示 | GET /api/agent/agents | §11.3 |

**验收标准**：可视化创建Agent→配置Prompt/Tools/Knowledge→测试对话→保存发布→Agent Studio可用。

**关键新建**：
```
后端（~8文件）：AgentConfigController, AgentToolController, PromptVersionController
前端（~3页面）：AgentBuilder.tsx, AgentTestConsole.tsx, AgentDetail.tsx（增强）
```

---

#### P1-2：Knowledge Graph (Neo4j) [3周]

> 目标：从"PG JSONB粗存"升级为"Neo4j图存储+Explorer+Graph RAG"
> 关联设计：10213 §13.10-13.25

| # | 任务 | 前端 | 后端 | 关联设计 |
|---|------|------|------|---------|
| 2.1 | Neo4j部署+接入 | — | Neo4j容器部署，Spring Data Neo4j配置 | §13.10 |
| 2.2 | 实体关系抽取 | — | Agent自动识别实体→建Node→抽Relation→写图 | §13.18 |
| 2.3 | Graph Explorer | React Flow图可视化（缩放/拖拽/展开/折叠）+ 节点详情面板 | GET /api/knowledge/graph（节点+边） | §13.24-13.25 |
| 2.4 | 图搜索 | 节点名搜索+关系类型搜索+路径搜索 | GET /api/knowledge/search?q=&type= | §13.12 |
| 2.5 | 路径分析 | 两节点间最短/全部路径 | POST /api/knowledge/path | §13.13 |
| 2.6 | Graph RAG基础 | 搜索→取子图→注入LLM上下文→增强回答 | POST /api/knowledge/graph-rag | §13.21 |

**验收标准**：Neo4j运行→Entity抽取落图→Explorer可浏览→输入问题Graph RAG返回增强回答。

**关键新建**：
```
后端（~10文件）：Neo4jConfig, GraphNodeRepository, GraphService, GraphRAGService
前端（~3页面）：GraphExplorer.tsx, PathAnalysis.tsx（增强COG页面）
基础设施：Neo4j Docker容器
```

---

#### P1-3：Pipeline 节点库 ✅ 已完成

Pipeline已从Workflow分离为独立实体，ML节点+Agent节点+调度中心就绪。

---

#### P1-4：Agent Mesh ✅ 已完成

Supervisor+Pipeline双模式，A2A MessageBus，10 REST端点，PG迁移完成。AgentMesh.tsx前端已对接。

---

#### P1-5：Data Catalog 完善 [2周]

> 目标：从"资产浏览"升级为"术语库+分类分级(Agent自动)+数据市场"
> 关联设计：102-数据目录 §2.4/2.5/2.12

| # | 任务 | 前端 | 后端 | 关联设计 |
|---|------|------|------|---------|
| 5.1 | 术语库CRUD | GlossaryManager页面（列表+创建+状态流转按钮） | GET/POST/PUT /api/glossary/terms | §2.4 |
| 5.2 | 术语关联资产 | 术语详情中绑定数据资产 | POST /api/glossary/terms/{id}/link | §2.4 |
| 5.3 | 分类分级面板 | 资产详情中显示分级+手动设置 | PUT /api/catalog/assets/{id}/classify | §2.5 |
| 5.4 | Agent自动分级 | 资产详情中"自动分级"按钮→Agent扫描字段识别PII | POST /api/catalog/assets/{id}/auto-classify | §2.5 |
| 5.5 | 数据市场 | Marketplace页面（热门/推荐/最新/高价值Tab） | GET /api/marketplace/assets | §2.12 |
| 5.6 | 访问申请 | 申请→审批工作流 | POST /api/marketplace/request-access | §2.12 |

**验收标准**：术语库完整CRUD→资产自动分级识别身份证/手机号→市场展示热门资产→申请审批闭环。

**关键新建**：
```
后端（~6文件）：GlossaryController, GlossaryService, ClassificationController, MarketplaceController
前端（~2页面）：GlossaryManager.tsx, Marketplace.tsx
DB：ecos_glossary_term, ecos_asset_classification
```

---

#### P1-6：统一查询层 [2周]

> 目标：ObjectQL+语义查询+NLQ原型
> 关联设计：10210 §10.5-10.10

| # | 任务 | 前端 | 后端 | 关联设计 |
|---|------|------|------|---------|
| 6.1 | ObjectQL引擎 | — | 解析ObjectQL语法→生成SQL（支持filter/sort/paginate/aggregate） | §10.5 |
| 6.2 | ObjectQL API | — | POST /api/query/objectql | §10.26 |
| 6.3 | 语义查询 | — | 语义词典（"高价值客户"→Score>80）→翻译为ObjectQL | §10.7-10.8 |
| 6.4 | NLQ原型 | 自然语言输入框 | POST /api/query/nlq（NL→语义解析→ObjectQL→执行→返回） | §10.9-10.10 |
| 6.5 | 查询历史 | 历史记录列表+复用 | GET/POST /api/query/history | §10.24 |

**验收标准**：输入ObjectQL JSON→返回查询结果→输入"华东高价值客户"→Agent解析语义→查到正确数据。

**关键新建**：
```
后端（~8文件）：ObjectQLParser, SemanticQueryEngine, NLQController, QueryHistoryService
前端：NLQ输入组件嵌入DataCatalog搜索栏
```

---

## 四、总工时

### P0 汇总

| 阶段 | 内容 | 后端 | 前端 | 合计 |
|------|------|------|------|------|
| P0-1 | Object Runtime 全链路 | 5d | 5d | 10d |
| P0-2 | Ontology Designer | 5d | 10d | 15d |
| P0-3 | Workflow Designer | 3d | 7d | 10d |
| P0-4 | World Model 后端 | 6d | 4d | 10d |
| P0-5 | Data Quality Dashboard | 4d | 4d | 8d |
| P0-6 | IAM 完善 | 3d | 2d | 5d |
| **合计** | | **26d** | **32d** | **58d** |

> **人天 = 1人×1天**。如果前后端各1人并行，**实际工期约8周**。

### P1 汇总

| 阶段 | 内容 | 合计 |
|------|------|------|
| P1-1 | Agent Builder | 15d |
| P1-2 | Knowledge Graph (Neo4j) | 15d |
| P1-3 | Pipeline 节点库 | 10d |
| P1-4 | Agent Mesh MVP | 15d |
| P1-5 | Data Catalog 完善 | 10d |
| P1-6 | 统一查询层 | 10d |
| **合计** | | **75d（~15周）** |

---

## 五、里程碑

| 里程碑 | 时间 | 交付物 | 演示能力 |
|--------|------|--------|---------|
| **M1** | 第2周末 | P0-1完成 | Object 完整CRUD + 状态机 + 关系浏览 |
| **M2** | 第5周末 | P0-2完成 | Ontology Designer 可视化建模 |
| **M3** | 第7周末 | P0-3完成 | Workflow Designer 拖拽设计流程 |
| **M4** | 第9周末 | P0-4完成 | World Model 因果推理 + What-If |
| **M5** | 第11周末 | P0-5完成 | Data Quality Dashboard |
| **M6** | 第12周末 | P0-6完成 | 完整 MVP（含 IAM） |

---

## 六、技术决策速查

| 决策点 | 选择 | 原因 |
|-------|------|------|
| 后端框架 | Spring Boot（复用 sysman-boot） | 已有认证/IAM/Agent/Workflow，零启动成本 |
| 数据库 | 扩展 sys-man schema，加 ecos_* 前缀表 | 不与现有 td_* 表冲突 |
| Ontology 存储 | PostgreSQL JSONB | MVP 规模可控，V2 迁 Neo4j |
| 图可视化 | React Flow（前端） | 已在 Workflow/Pipeline 使用 |
| Agent LLM | DeepSeek v4-flash | 已接入，支持 function calling |
| 向量检索 | PostgreSQL 原生数组 + BGE | 已实现，零额外依赖 |
| IAM | JWT + 数据库角色 | 已有基础，扩展即可 |
| 工作流引擎 | 自研状态机（继续增强） | 已有 Agent Node 集成，Temporal 延后 |

---

## 七、与设计文档的对齐声明

| 设计文档要求 | 本计划覆盖 | 说明 |
|-------------|-----------|------|
| 20个模块 | P0覆盖6个核心模块 | P1/P2覆盖剩余14个 |
| PostgreSQL + Neo4j | P0用PG(JSONB)，P1引入Neo4j | 分阶段迁移 |
| Temporal 工作流 | P0自研状态机 | V1评估迁移 |
| Kafka 事件总线 | P0用Spring Event | V1引入Kafka |
| 80页面 | P0新增约20页面 | 核心闭环优先 |
| 40-80人团队 | 1-2人可执行P0 | MVP精简策略 |

---

## 八、立即执行的第一步

| 优先级 | 任务 | 产出 |
|--------|------|------|
| P0 | 在 `sys-man` 数据库建 P0 所需新表 | DDL 执行 |
| P0 | 创建 ObjectController 全链路 CRUD | Java 源码 |
| P0 | 前端 ObjectExplorer 重写 | 完整 CRUD 页面 |
| P0 | Ontology Entity/Property/Relationship Controller | 后端 CRUD 就绪 |
