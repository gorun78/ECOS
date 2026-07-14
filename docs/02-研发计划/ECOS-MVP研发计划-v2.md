# ECOS MVP 研发计划

> 日期：2026-06-13  
> 基于：差距分析报告 + 现有实现（126 Java 文件 / 54 端点 / 10 前端页面）  
> 原则：**Ontology First → Agent Native → Mission Driven → Incremental Delivery**

---

## 当前状态基线

| 模块 | 完成度 | 关键问题 |
|------|--------|---------|
| IAM | 60% | 缺 SSO/MFA |
| Ontology | 90% | 基本完整 |
| Object Runtime | 75% | 缺权限/版本/搜索 |
| Workflow | 70% | 自研状态机，缺并行/SLA |
| Knowledge | 40% | **全模拟**（RAG、搜索、图谱） |
| Agent Runtime | 35% | **核心全模拟**（Planner、Executor、Tool） |
| Mission | 15% | 仅 3 个轻量端点 |
| 前端 c2eos | 10/10 页对接 | P1 刚完成 |

---

## 总策略

```
Phase A: Agent 真实化 ──── 2 周 ──── 最大演示价值
Phase B: Knowledge 真实化 ─ 2 周 ──── 第二大演示价值
Phase C: Workflow 升级 ──── 1 周 ──── 串联 Agent+Knowledge
Phase D: IAM 补全 ──────── 1 周 ──── 安全合规基础
Phase E: Mission Control ── 1 周 ──── 运营管理面
Phase F: 体验与数据 ────── 1 周 ──── 演示品质
```

**总周期：8 周（2 个月单人全职）**

---

## Phase A：Agent Runtime 真实化 [2 周]

> 目标：Agent 从"模拟回复"变为"真实 LLM 驱动 + 真实工具调用"

### A1 — LLM 接入层（3 天）

| # | 任务 | 说明 |
|---|------|------|
| A1.1 | 抽象 LLM Provider 接口 | 支持 DeepSeek/Qwen/OpenAI 切换 |
| A1.2 | 实现 DeepSeek Provider | 复用现有 `application.yml` 配置 |
| A1.3 | 实现流式响应（SSE） | 前端 `AgentStudio` 支持打字机效果 |
| A1.4 | Token 用量统计 | 每次调用记录 tokens/延迟/费用 |

**关键文件：** 新建 `llm/` package，含 `LlmProvider` 接口 + `DeepSeekProvider` + `OpenAiProvider`

### A2 — Planner 真实化（3 天）

| # | 任务 | 说明 |
|---|------|------|
| A2.1 | 设计 Planner Prompt 模板 | System prompt + tool descriptions + Few-shot |
| A2.2 | 实现 ReAct 循环 | Thought → Action → Observation → ... → Final Answer |
| A2.3 | 工具选择路由 | 根据用户意图自动选择工具（object-query / knowledge-search / ontology-explore） |
| A2.4 | 多步推理 | 支持单次对话中调用多个工具 |
| A2.5 | Thought Trace 记录 | 每一步推理存入 `ecos_agent_execution` |

### A3 — Tool 真实调用（2 天）

| # | 任务 | 说明 |
|---|------|------|
| A3.1 | `object-query` 工具 | 真实查询 `ecos_object_data` 表 |
| A3.2 | `knowledge-search` 工具 | 调用 Knowledge Service 的 RAG 接口（Phase B 对接） |
| A3.3 | `ontology-explore` 工具 | 查询 ontology entities/relationships |
| A3.4 | `workflow-start` 工具 | 触发工作流实例 |
| A3.5 | 工具调用审计日志 | 每次工具调用写入 `ecos_agent_execution` |

### A4 — Agent Memory（1 天）

| # | 任务 | 说明 |
|---|------|------|
| A4.1 | 会话级上下文 | 同一次对话历史注入 prompt |
| A4.2 | 跨会话记忆（可选） | 摘要上次对话，注入下次 |

### A5 — 前端对接（1 天）

| # | 任务 | 说明 |
|---|------|------|
| A5.1 | AgentStudio 流式渲染 | SSE → 逐字显示 |
| A5.2 | Thought Trace 真实展示 | 每步工具调用可视化 |
| A5.3 | 工具调用进度条 | 显示当前执行的工具 |

**Phase A 验收标准：**
- Agent 对话由真实 LLM 驱动
- 至少 2 个工具真实执行（object-query / knowledge-search）
- 多步推理可追踪
- 流式响应可感知

---

## Phase B：Knowledge Center 真实化 [2 周]

> 目标：从 MySQL LIKE 模拟升级为向量检索 + 真实 RAG

### B1 — 向量数据库选型与部署（1 天）

| # | 方案 | 优劣势 |
|---|------|--------|
| 方案 1 | **pgvector**（PostgreSQL 扩展） | 需换库，与设计文档对齐 |
| 方案 2 | **Qdrant**（独立向量库） | 性能好，但多一个组件 |
| 方案 3 | **ChromaDB**（轻量嵌入式） | 最简单，Python 生态 |

**推荐方案 3 ChromaDB**：MVP 阶段最小成本，Java 通过 HTTP API 调用，后续可升级到 pgvector

### B2 — 文档处理管道（2 天）

| # | 任务 | 说明 |
|---|------|------|
| B2.1 | 文档上传接口 | 支持 .txt/.md/.pdf/.docx |
| B2.2 | 文档分块（Chunking） | 按段落/语义分块，overlap 控制 |
| B2.3 | 增量索引 | 新文档自动分块→向量化→入库 |
| B2.4 | 元数据提取 | 标题/来源/日期/标签 |

### B3 — Embedding 服务（1 天）

| # | 任务 | 说明 |
|---|------|------|
| B3.1 | Embedding Provider 抽象 | 支持多个模型切换 |
| B3.2 | 默认使用 DeepSeek Embedding | 复用现有 API key |
| B3.3 | 批量向量化 | 并行处理大量文档 |

### B4 — 搜索与 RAG（3 天）

| # | 任务 | 说明 |
|---|------|------|
| B4.1 | 向量相似搜索 | 替换 MySQL LIKE |
| B4.2 | RAG 检索增强生成 | 检索 Top-K → 拼接 prompt → LLM 生成 |
| B4.3 | 混合搜索 | 向量 + 关键词 BM25 融合 |
| B4.4 | 搜索结果高亮 | 返回匹配片段 |

### B5 — 知识图谱增强（2 天）

| # | 任务 | 说明 |
|---|------|------|
| B5.1 | 实体关系抽取 | 从文档自动抽取实体+关系 |
| B5.2 | 图谱节点自动创建 | 抽取出的人/组织/产品 → knowledge node |
| B5.3 | 图谱查询 API | 支持 1-hop/2-hop 邻居查询 |

### B6 — 前端对接（1 天）

| # | 任务 | 说明 |
|---|------|------|
| B6.1 | Knowledge Center 页面 | 搜索框 + 结果卡片 + RAG 回答 |
| B6.2 | Graph Explorer 增强 | 点击节点查看详情 |

**Phase B 验收标准：**
- 文档上传→自动分块→向量化→可搜索
- RAG 问答返回基于真实文档的答案
- 知识图谱节点数 > 20

---

## Phase C：Workflow 升级 [1 周]

> 目标：从简单状态机升级为可串联 Agent 的流程引擎

### C1 — 并行与分支（2 天）

| # | 任务 | 说明 |
|---|------|------|
| C1.1 | 并行网关节点 | AND-split / AND-join |
| C1.2 | 条件分支 | 基于变量值的条件路由 |
| C1.3 | 子流程调用 | workflow 中调用另一个 workflow |

### C2 — Agent 节点集成（2 天）

| # | 任务 | 说明 |
|---|------|------|
| C2.1 | Agent 任务节点 | Workflow 节点类型新增 "AGENT" |
| C2.2 | Agent 节点执行 | 调用 Phase A 的 Agent Runtime |
| C2.3 | 结果回传 | Agent 输出作为 workflow 变量 |
| C2.4 | 审批+Agent 混合流程 | 人工审批 → Agent 分析 → 自动决策 |

### C3 — SLA 与超时（1 天）

| # | 任务 | 说明 |
|---|------|------|
| C3.1 | 节点超时配置 | 每个节点可设 timeout |
| C3.2 | 超时处理策略 | 重试 / 跳过 / 失败 |
| C3.3 | SLA 告警 | 超时节点推送至 MonitoringCenter |

**Phase C 验收标准：**
- 支持并行分支 + 条件路由
- Agent 节点可在 Workflow 中真实执行
- 超时节点自动告警

---

## Phase D：IAM 补全 [1 周]

> 目标：从"有 CRUD"升级为"可生产使用"

### D1 — SSO 单点登录（2 天）

| # | 任务 | 说明 |
|---|------|------|
| D1.1 | OAuth2/OIDC 集成 | 支持 Keycloak / 企业微信 |
| D1.2 | JWT Token 签发 | 登录成功返回 access_token + refresh_token |
| D1.3 | Token 刷新 | refresh_token 换新 access_token |

### D2 — 权限增强（2 天）

| # | 任务 | 说明 |
|---|------|------|
| D2.1 | 数据权限过滤 | 查询时根据用户角色过滤可见数据 |
| D2.2 | 操作审计 | 所有 CUD 操作写入 audit_log |
| D2.3 | 前端路由守卫 | 未登录跳转登录页 |

### D3 — 前端登录页（1 天）

| # | 任务 | 说明 |
|---|------|------|
| D3.1 | Login 页面 | 用户名/密码 + 企业微信扫码 |
| D3.2 | 登录态管理 | React Context + localStorage |

**Phase D 验收标准：**
- 支持用户名密码登录 + 企业微信 OAuth
- 不同角色看到不同菜单/数据
- 操作审计可追溯

---

## Phase E：Mission Control [1 周]

> 目标：从 3 个轻量端点到完整的运营管理中心

### E1 — 任务管理（2 天）

| # | 任务 | 说明 |
|---|------|------|
| E1.1 | Task CRUD | 创建/分配/跟踪任务 |
| E1.2 | 任务看板 | Kanban 视图（Todo/In Progress/Done） |
| E1.3 | 任务关联 | Task → Workflow Instance / Agent Execution |

### E2 — 目标管理（1 天）

| # | 任务 | 说明 |
|---|------|------|
| E2.1 | OKR 目标树 | Objective → Key Result 层级 |
| E2.2 | 目标进度 | 自动计算完成百分比 |

### E3 — 运营 Dashboard（2 天）

| # | 任务 | 说明 |
|---|------|------|
| E3.1 | 实时统计面板 | Agent 执行次数/成功率/平均延迟 |
| E3.2 | Workflow 统计 | 各状态流程数/平均耗时 |
| E3.3 | 趋势图表 | 7 天/30 天趋势线 |

**Phase E 验收标准：**
- 任务可创建/分配/跟踪
- Dashboard 显示实时统计数据
- 目标树可展开

---

## Phase F：体验与数据 [1 周]

> 目标：演示品质提升

### F1 — 种子数据扩展（1 天）

| # | 任务 | 说明 |
|---|------|------|
| F1.1 | 数据集扩充到 10+ | 覆盖多行业场景 |
| F1.2 | 对象数据扩充到 50+ | Customer/Supplier/Invoice/Product 各 10+ 条 |
| F1.3 | 知识文档扩充到 20+ | 上传真实文档（政策/规范/报告） |
| F1.4 | Workflow 扩充到 5+ | 展示不同类型的流程 |

### F2 — 全局 Loading/Error/Empty 态（1 天）

| # | 任务 | 说明 |
|---|------|------|
| F2.1 | 全局 Loading 组件 | Skeleton 占位 |
| F2.2 | 全局 Error 组件 | 友好错误提示 + 重试按钮 |
| F2.3 | 全局 Empty 组件 | 空数据引导提示 |

### F3 — 演示脚本（2 天）

| # | 任务 | 说明 |
|---|------|------|
| F3.1 | Demo 场景 1：Ontology 建模 | 创建实体→定义属性→关联关系→生成对象 |
| F3.2 | Demo 场景 2：Agent 分析 | Agent 查询 Customer 数据→发现异常→触发工作流 |
| F3.3 | Demo 场景 3：知识问答 | 上传政策文档→RAG 问答→知识图谱展示 |
| F3.4 | Demo 场景 4：流程自动化 | 审批流程→Agent 节点自动判断→结果通知 |

### F4 — 构建与部署（1 天）

| # | 任务 | 说明 |
|---|------|------|
| F4.1 | 前端 production build | `npm run build` → 静态资源优化 |
| F4.2 | 后端 Docker 化 | Dockerfile + docker-compose |
| F4.3 | 一键启动脚本 | `docker-compose up` 启动全部服务 |
| F4.4 | 部署文档 | 部署步骤/环境变量/端口说明 |

**Phase F 验收标准：**
- 4 个 Demo 场景可流畅演示
- `docker-compose up` 一键启动
- 无白屏/无 console error

---

## 里程碑

| 里程碑 | 时间 | 交付物 | 演示能力 |
|--------|------|--------|---------|
| **M1** | 第 2 周末 | Phase A 完成 | Agent 真实对话 + 工具调用 |
| **M2** | 第 4 周末 | Phase B 完成 | 文档上传 + RAG 问答 + 知识图谱 |
| **M3** | 第 5 周末 | Phase C 完成 | Agent 驱动的自动化工作流 |
| **M4** | 第 6 周末 | Phase D 完成 | 登录 + 权限 + 审计 |
| **M5** | 第 7 周末 | Phase E 完成 | 运营 Dashboard |
| **M6** | 第 8 周末 | Phase F 完成 | **完整 MVP 演示** |

---

## 技术债务跟踪

以下差距**不在 MVP 范围内**，记录为 V1 技术债务：

| # | 项目 | 设计文档要求 | V1 处理方案 |
|---|------|-------------|-----------|
| T1 | PostgreSQL 迁移 | 设计基准数据库 | 评估 MySQL→PG 迁移成本 |
| T2 | Neo4j 图数据库 | 知识图谱存储 | 评估是否需要（ChromaDB 可能够用） |
| T3 | Temporal 工作流引擎 | 设计明确要求 | 评估接入复杂度 vs 自研收益 |
| T4 | Redis 缓存 | 会话/热点数据 | 按需引入 |
| T5 | Kafka 消息队列 | 事件驱动架构 | 工作流异步触发时需要 |
| T6 | Spring Cloud 微服务 | 服务拆分 | V1 按模块拆微服务 |
| T7 | Pipeline 平台 | 第 3 章 | 全新模块，需 4 周+ |
| T8 | Action/Rule Engine | 第 8 章 | 全新模块，需 3 周+ |
| T9 | Enterprise App Studio | 第 17 章 | 全新模块，需 6 周+ |
| T10 | Agent Marketplace | 设计 V1 范围 | 需多租户 + 发布审核 |

---

## 资源估算

| 角色 | 人数 | 周期 |
|------|------|------|
| 全栈开发（后端 Java + 前端 React） | 1 人 | 8 周 |
| AI/LLM 集成 | 同一人 | 含在 Phase A/B |
| DevOps | 同一人 | Phase F.4 |

**实际所需：1 名全栈工程师 × 8 周（约 320 小时）**

---

## 与设计文档 MVP 的对齐

| 设计 MVP 要求 | 本计划实现 | 说明 |
|--------------|-----------|------|
| IAM + SSO + MFA | IAM + SSO（MFA 延后） | MVP 可接受 |
| Ontology Studio | ✅ 已完成 | - |
| Object Runtime | ✅ 已完成 | - |
| Workflow（Temporal） | Workflow（自研升级版） | 自研够用，V1 评估 Temporal |
| Knowledge（Vector+RAG） | Knowledge（ChromaDB+RAG） | 技术选型不同，能力对齐 |
| Agent（真实 LLM） | Agent（真实 LLM+Tool） | 本计划核心交付 |
| Mission Dashboard | Mission Control | 本计划补齐 |
| PostgreSQL + Neo4j | MySQL + ChromaDB | 简化但功能对齐 |
| 80 页面 | 10 页面（c2eos） | 页面数不对齐，功能覆盖对齐 |

**结论：本计划在 8 周内可交付一个功能对齐设计 MVP 的可用系统，技术栈有简化但不影响演示价值。**
