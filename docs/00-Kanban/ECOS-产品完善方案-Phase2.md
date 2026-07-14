# ECOS 产品完善方案 Phase 2 — 智能化

> **编制**: ECOS-PMO  
> **日期**: 2026-06-24  
> **基于**: Phase 1 产品化方案路线图（第220行起）  
> **周期**: 8周（Week 7–14，目标 2026-09-28）  
> **前提**: Phase 1 全部 7 子项目已完成，25 端点验证通过，前端 24 页全部 api.ts 化

---

## 一、Phase 1 验收确认

| 子项目 | 状态 | 验证方式 |
|--------|:--:|---------|
| P1-1 安全配置面板 | ✅ | `GET/PUT /api/v1/security/profile` |
| P1-2 密码学审计账本 | ✅ | `POST /record` + `GET /verify` |
| P1-3 世界模型产品化 | ✅ | 21 目标金字塔，5-Tab 前端 |
| P1-4 数据市场产品化 | ✅ | `GET/POST /api/marketplace/assets` |
| P1-5 IAM+ABAC | ✅ | 13 用户/5 角色/ABAC CRUD |
| P1-6 门户搜索 | ✅ | 9 表跨域搜索 |
| P1-7 测试补齐 | ✅ | 40 cases, 100% 通过 |
| S19 物理表注册 | ✅ | 115 表采集, 资源浏览面板 |

**Phase 1 结论**: 全部达标，可以进入 Phase 2。

---

## 二、Phase 2 目标

> **"智能化"** — 从"能跑"到"能用 AI 辅助决策"

| 对比维度 | Phase 1（产品化） | Phase 2（智能化） |
|---------|-----------------|-----------------|
| 知识查询 | 内存 stream 过滤 | Neo4j Cypher 图遍历 |
| Agent 编排 | 配置管理 | 真实 ReAct 循环联动 |
| 安全策略 | 硬编码 Java | OPA Rego 热加载 |
| 根因分析 | 静态情景对比 | 因果图可视化 + 情景推演 |
| 告警 | 无 | 规则引擎 + WebSocket 实时推送 |
| 决策沉淀 | 无 | 案例检索 + 自学习库 |

---

## 三、子项目详设

### P2-1: Neo4j 知识图谱集成（Week 7-8）

> **替换 dccheng 的 Ontology 关系查询（当前 stream 过滤 → Cypher 图遍历）**

**现状**: 知识图谱数据存在 PostgreSQL `td_knowledge_node/edge` 表，查询用 Java stream filter。
**目标**: 部署 Neo4j 社区版，建立数据同步管道，Ontology 关系查询切换至 Cypher。

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P2-1.1 | Neo4j Docker 部署 | `docker-compose`，端口 7474/7687 | BE |
| P2-1.2 | PG→Neo4j 数据同步 | ETL Job（Flyway 迁移 + 全量同步脚本） | BE |
| P2-1.3 | Cypher 查询服务 | `Neo4jQueryService`，支持 3 类查询（路径/邻居/全图） | BE |
| P2-1.4 | KnowledgeController 改造 | 查询路径从 JDBC→Cypher，保留 PostgreSQL 降级 | BE |
| P2-1.5 | 知识图谱前端升级 | 关系路径高亮 + 节点展开/收起 + 图统计面板 | FE |
| P2-1.6 | 回归验证 | 原 23 节点/18 边数据迁移后一致 | QA |

**验收标准**: `GET /api/knowledge/graph` 从 Neo4j 返回，数据量一致。前端图支持路径高亮。

---

### P2-2: Agent Mesh 真实编排（Week 7-10）

> **Coordinator → Researcher → Analyst 链路，实现 ReAct 循环**

**现状**: Agent 配置和 Mesh 管理框架已有，`/api/agent-mesh/agents` 返回 1 个 Agent。
**目标**: 实现真正的 ReAct 循环，Coordinator 拆解任务 → Researcher 调用工具 → Analyst 汇总输出。

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P2-2.1 | ReAct 引擎核心 | `ReActLoop` 类：Thought→Action→Observation 循环，最多 10 轮 | BE |
| P2-2.2 | ToolRegistry 工具注册 | 4 个内置工具：知识搜索、对象查询、本体浏览、工作流启动 | BE |
| P2-2.3 | Mission 编排器 | Coordinator 拆解 mission → 分配 tasks → 聚合 results | BE |
| P2-2.4 | Agent Mesh 前端 | 任务可视化：甘特图/时间线 + 实时状态轮询 | FE |
| P2-2.5 | Agent Chat 升级 | 支持多轮对话 + 中间思考过程展示（thought trace） | FE |
| P2-2.6 | E2E 验证 | "查询客户订单" → Coordinator→Researcher→Analyst 全链路 | QA |

**验收标准**: 发起一个 Mission（如"分析客户张三的订单趋势"），在 Agent Mesh 页面看到任务拆解→执行→输出的完整链路。

---

### P2-3: OPA 策略动态加载（Week 9-10）

> **当前 ABAC 策略硬编码在 InMemoryAbacPolicyService，改为 OPA 引擎热加载**

**现状**: ABAC 策略存内存 ConcurrentHashMap，重启丢失。
**目标**: 部署 OPA，策略存为 Rego 文件，支持热加载和版本管理。

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P2-3.1 | OPA 部署 + SDK 集成 | Docker OPA + `opa-java` 依赖 | BE |
| P2-3.2 | Rego 策略模板 | 5 个策略模板：RBAC、ABAC、时间窗口、IP 限制、数据脱敏 | BE |
| P2-3.3 | PolicyEngine 改造 | `PolicyEngineController` 从内存→OPA REST API 调用 | BE |
| P2-3.4 | 策略管理前端 | Rego 编辑器（语法高亮）+ 版本管理 + 热加载开关 | FE |
| P2-3.5 | 回归验证 | 原 ABAC CRUD 端点 + 新增 OPA 策略评估端点 | QA |

**验收标准**: 修改 Rego 策略文件 → 5 秒内自动生效。ABAC 策略 CRUD 保持兼容。

---

### P2-4: 因果图可视化与情景推演增强（Week 11-12）

> **从"因果推理引擎"降级为"因果推演"** — 对齐 PRD 降级共识，基于现有数据基础

**背景与决策**: Phase 1 启动会上 PM + ARCH 已达成共识——在数据基础不足时，完整因果推理（SCM + Do-calculus）是空中楼阁。经 Phase 2 审核确认：当前 WorldModel 的 21 个目标/情景数据不足以支撑结构化因果模型；ARCH 指出 Commons Math3 不提供 SCM/Do-calculus 现成实现，Tetrad（CMU 因果推断库）虽功能完备，但为 **GPL 许可证**且要求 **Java 21**（ECOS 运行于 Java 17），集成风险高。

**决策**: P2-4 **不引入 Tetrad**，降级为"因果图可视化 + 情景对比增强"，不再追求 SCM/Do-calculus/贝叶斯反推。完整因果推理推迟到 Phase 3（待 ≥50 条因果场景数据就绪后重新评估）。

**现状**: WorldModelViewer 展示情景对比（静态），无因果可视化，亦无可交互的 what-if 推演。
**目标**: 构建因果图可视化 + 手动情景推演面板。用户在因果图上选择干预节点，查看关联目标的变化趋势。

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P2-4.1 | 因果图数据模型 | `CausalGraph` POJO：节点（WorldModel 目标）+ 有向边（影响关系），存储于 Neo4j | BE |
| P2-4.2 | 因果图 API | `GET /api/causal/graph`（返回图结构）+ `GET /api/causal/paths?from=X&to=Y`（Neo4j 路径查询） | BE |
| P2-4.3 | 情景对比增强 | `POST /api/causal/compare`：输入两个情景参数集，返回各目标维度的差异对比表（基于 WorldModel 现有评分） | BE |
| P2-4.4 | 因果图可视化前端 | WorldModelViewer 第6 Tab：DAG 力导向图（Cytoscape.js）+ 节点点击展开详情 + 路径高亮 | FE |
| P2-4.5 | 情景推演面板 | what-if 干预面板：用户拖拽修改输入变量 → 实时对比目标变化（前端驱动，无需后端 SCM） | FE |
| P2-4.6 | 验证 | 定义 ≥8 条因果边（如"数据质量→决策准确度"），因果图可遍历，情景对比表输出差异列 | QA |

**验收标准**:
1. `GET /api/causal/graph` 返回 ≥8 个节点 + ≥8 条有向边，前端 Cytoscape.js 正确渲染 DAG
2. `POST /api/causal/compare` 输入两组参数，返回 ≥5 个目标维度的差异对比
3. 前端 what-if 面板：修改 ≥3 个输入变量后，对比视图在 1 秒内更新

---

### P2-5: 自学习案例库（Week 12-14）

> **决策记忆沉淀 + 对比学习 + 案例检索召回**

**现状**: 无历史决策记录和检索能力。
**目标**: 记录每次因果推演和 Agent 决策，支持相似情景检索。

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P2-5.1 | 案例实体 + 建表 | `ecos_decision_case` 表：情景嵌入向量 + 决策 + 结果 + 反馈 | BE |
| P2-5.2 | 案例记录服务 | `CaseRecorder`：Agent/因果引擎执行后自动记录 | BE |
| P2-5.3 | 相似案例检索 | `CaseRetriever`：余弦相似度 Top-K 检索（PG vector） | BE |
| P2-5.4 | CaseController | `POST /cases/record` + `GET /cases/search?q=xxx&k=5` | BE |
| P2-5.5 | 案例库前端 | 案例列表 + 相似度搜索 + 详情页（含情景嵌入可视化） | FE |
| P2-5.6 | 回归验证 | 记录 5 条案例 → 检索相似度排序正确 | QA |

**验收标准**: 执行一次因果推演后自动记录案例。输入相似关键词可检索到历史案例。

---

### P2-6: 分级告警 + WebSocket 推送（Week 13-14）

> **告警规则引擎 + 实时推送 + 强制确认通道**

**现状**: MonitoringCenter 页面展示静态数据，无实时告警。
**目标**: 告警规则配置 + WebSocket 实时推送 + 分级（INFO/WARN/CRITICAL）。

| ID | 任务 | 产出 | 负责 |
|----|------|------|:--:|
| P2-6.1 | 告警规则引擎 | `AlertRuleEngine`：规则 DSL + 条件评估 + 去重窗口 | BE |
| P2-6.2 | WebSocket 服务 | Spring WebSocket + STOMP，`/ws/alerts` 频道 | BE |
| P2-6.3 | AlertController | CRUD 规则 + `POST /alerts/test` + `GET /alerts/history` | BE |
| P2-6.4 | 告警前端面板 | MonitoringCenter 增加实时告警卡片 + 分级颜色 + 确认按钮 | FE |
| P2-6.5 | WebSocket 前端 | STOMP.js 订阅 `/ws/alerts` + 桌面通知 | FE |
| P2-6.6 | 端到端验证 | 创建规则→触发告警→WebSocket 推送→前端弹窗→确认关闭 | QA |

**验收标准**: 配置一条规则（如"DQ 评分 < 80"），触发后 2 秒内前端收到推送。

---

## 四、并行策略

```
Week │ 7-8  │ 9-10 │ 11-12 │ 13-14 │
─────┼──────┼──────┼───────┼───────┤
P2-1 │ ████ │      │       │       │ Neo4j + 知识图谱
P2-2 │ ████ │ ████ │       │       │ Agent Mesh 编排
P2-3 │      │ ████ │       │       │ OPA 策略
P2-4 │      │      │ ████  │       │ 因果图可视化+推演
P2-5 │      │      │ ████  │ ████  │ 自学习案例库
P2-6 │      │      │       │ ████  │ 告警+WebSocket
─────┼──────┼──────┼───────┼───────┤
QA   │ 回归 │ 回归 │ 回归  │ 回归  │ 每2周全端点回归
```

---

## 五、技术栈

| 组件 | Phase 1 | Phase 2 新增 |
|------|---------|-------------|
| 数据库 | PostgreSQL 14 | + Neo4j 5 社区版 |
| 安全引擎 | Spring Security | + OPA 0.63 |
| 消息推送 | — | + Spring WebSocket + STOMP |
| 因果推演 | — | + Cytoscape.js（前端图渲染）+ Neo4j Cypher 路径查询 |
| 向量检索 | — | + pgvector 扩展 |
| 前端 | React 19 + Vite | 不变 |

**原则**: Neo4j/OPA 用 Docker 部署，不引入 K8s。pgvector 是 PG 扩展，不引入新数据库。

---

## 六、验收清单

- [ ] P2-1: `/api/knowledge/graph` 从 Neo4j 返回，数据一致
- [ ] P2-2: Agent Mission 全链路 ReAct 执行成功
- [ ] P2-3: Rego 策略修改后 5 秒内热加载生效
- [ ] P2-4: 因果图 ≥8 节点/8 边 DAG 渲染 + 情景对比 ≥5 维度差异输出
- [ ] P2-5: 5 条案例检索，Top-1 命中率 100%
- [ ] P2-6: 告警触发→WebSocket 推送 < 2 秒
- [ ] 全端点回归: 40+ 端点 200

---

> **Phase 2 最后一句话：不做没有后端支撑的前端页面，不引入超出团队运维能力的基础设施。每个子项目都是"后端 API 先通→前端再上→QA 验证"的顺序。**
