# ECOS + c2eos 后续完善计划

> 日期：2026-06-12  
> 当前状态：Phase 1-6 后端 54 端点完工，c2eos 前端 5/10 页面对接真实数据

---

## P0 — 阻塞性 Bug（修了才能完整演示）

| # | 问题 | 根因 | 方案 | 估时 |
|---|------|------|------|------|
| 1 | `POST /api/v1/ecos/objects/{entity}` 500 | Jackson 无法序列化 MySQL JSON 列 | 加 `@JsonRawValue` 或自定义 TypeHandler | 1h |
| 2 | 中文搜索 URL 报 400（Tomcat RFC 7230） | 未 encodeURIComponent | 前端统一编码，或后端配 `relaxedQueryChars` | 0.5h |

---

## P1 — 补齐对接（让全部页面用真实数据）

### 1. 页面替换 mock → API（6 页）

| 页面 | 当前数据源 | 所需 API | 后端是否有 |
|------|-----------|---------|-----------|
| **DatasetExplorer** | MOCK_DATA_ASSETS | `GET /api/datasets/:id` + preview | ✅ 有 |
| **PipelineBuilder** | INITIAL_PIPELINE_NODES | `GET /api/v1/ecos/workflows` | ✅ 有（Workflow 模块） |
| **MonitoringCenter** | INITIAL_LINEAGE_* | `GET /api/v1/ecos/objects/relations` | ✅ 有（Object 模块） |
| **CodeWorkbook** | MOCK_DATA_ASSETS | `GET /api/datasets` | ✅ 有 |
| **CognitiveOperatingSystem** | MOCK_KNOWLEDGE_* | `GET /api/knowledge/graph` | ❌ 需新建 |
| **OperationalApps** | 部分 mock | action execute 已有，补齐列表 | 半有 |

### 2. 后端补齐缺失端点

| 端点 | 用途 | 复杂度 |
|------|------|--------|
| `GET /api/knowledge/graph` | 知识图谱节点+边 | 低 — 已有 `ecos_knowledge_*` 表 |
| `GET /api/agent/agents` | c2eos Agent 列表 | 低 — 已有 `AgentController`，加 c2eos 适配 |
| `GET /api/agent/tools` | c2eos 工具列表 | 低 — 已有 `ecos_agent_tool` 表 |
| `GET /api/audit-logs` | 真实审计日志 | 已有 BFF 实现，需调 Agent 执行产生数据 |

### 3. 数据补齐

| 问题 | 方案 |
|------|------|
| Supplier/Invoice 实体 0 属性 | `fetchOntology()` 中补充 fallback 定义 |
| 种子数据偏少（5 数据集） | 扩种子 SQL 到 8-10 条，丰富展示效果 |

---

## P2 — 体验优化

| # | 项目 | 说明 |
|---|------|------|
| 1 | **全局 Loading/Error 态** | 目前仅 OntologyExplorer 有，统一下其余页面 |
| 2 | **空状态占位** | 数据为空时不白屏，显示友好提示 |
| 3 | **c2eos 页面 Tab 切换动画** | 当前无过渡 |
| 4 | **审计日志页有真实数据** | Agent 执行后自动产生审计记录 |
| 5 | **深色主题适配** | 已有 4 套主题，但新改页面需验证 |
| 6 | **清理 22 张旧 v1 空表** | `DROP TABLE IF EXISTS` — 确认无引用后删 |
| 7 | **npm 构建产物优化** | `npm run build` 验证，gzip 压缩 |

---

## P3 — 架构增强（v2）

| 项目 | 说明 | 依赖 |
|------|------|------|
| 前端认证 | 对接 sysman IAM 登录 | 已有 JWT 后端 |
| Neo4j 知识图谱 | 替代 MySQL LIKE 搜索 | 需部署 Neo4j |
| ClickHouse 时序数据 | PlantOps 类实时数据 | 需部署 ClickHouse |
| Kafka 事件流 | Workflow 异步触发 | 需部署 Kafka |
| WebSocket 推送 | Agent 执行实时推送 trace | 已有 FastAPI bridge 模板 |

---

## 建议执行顺序

```
W1: P0 两个 Bug → P1 补齐 Knowledge Graph API
W2: P1 替换 DatasetExplorer + PipelineBuilder + MonitoringCenter + CodeWorkbook
W3: P1 补齐 Agent tools API + 种子数据扩充 + Supplier/Invoice 修复
W4: P2 全局 Loading 态 + 空状态 + 审计日志
```

**总估时：约 3-4 天（按 4h/天有效编码时间）**

---

## 当前已完成清单（留存）

| 模块 | 表 | 文件 | 端点 | 前端对接 |
|------|-----|------|------|---------|
| IAM | 已有 | 已有 | 14 | - |
| Ontology | 4 | 21 | 6 | ✅ OntologyExplorer |
| Object Runtime | 2 | 10 | 7 | ✅ DataCatalog |
| Workflow | 5 | 15 | 10 | 未对接 |
| Knowledge | 3 | 10 | 7 | 未对接 |
| Agent Runtime | 4 | 9 | 10 | ✅ AgentStudio |
| **合计** | **18** | **65+** | **54** | **5/10 页** |
