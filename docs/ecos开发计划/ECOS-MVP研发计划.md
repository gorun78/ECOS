# ECOS MVP 研发计划

> 基于 `18-MVP实施路线图.txt` + ECOS 设计文档 + Databridge-v2 现有资产

---

## 一、战略定位

```
c2eos (Next.js) ──前端──▶ ECOS Portal
Databridge (Java) ──后端──▶ ECOS Backend (sysman-boot 扩展)
```

**核心决策**：ECOS **不是**从零新建工程，而是在 Databridge-v2 的 sysman 基础上渐进式扩展。

---

## 二、Databridge → ECOS 后端资产映射

| ECOS 模块 | Databridge 已有能力 | 差距 |
|----------|-------------------|------|
| **Identity & IAM** | ✅ sysman: 用户/角色/权限/机构/菜单，完整 CRUD | ⚠️ 需对接 ECOS 设计的 `ecos_identity` schema（目前用 `sys-man`） |
| **Ontology Studio** | ❌ 全新模块 | 🔴 需新建 ontology/* 四层（Entity→Repo→Service→Controller） |
| **Object Runtime** | ❌ 全新模块 | 🔴 需新建：基于 Ontology 定义动态生成 CRUD API |
| **Workflow Engine** | ⚠️ buszhi 有流程定义（未迁移完成） | 🟡 可用 Temporal 或简化版状态机 |
| **Knowledge Center** | ⚠️ 知识库已有 RAG 实践 | 🟡 需产品化：文档管理 + 向量搜索 + Graph RAG |
| **Agent Runtime** | ✅ hermes-engine + AgentProfile CRUD | ⚠️ 需增强：Tool 管理、Planner/Executor 可视化、评估体系 |
| **Agent Studio** | ✅ sysman Agent Profile 管理 | 🟡 已有 CRUD 基础，缺 Agent 编排/测试/发布流程 |
| **Mission Dashboard** | ❌ 全新 | 🟢 数据聚合页面，轻量 |

### 后端新增 Schema 清单

在 `sys-man` 数据库新增以下表（ECOS schema）：

```sql
-- Ontology（Phase 2）
CREATE TABLE IF NOT EXISTS ecos_ontology (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    version VARCHAR(20) DEFAULT '1.0',
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ecos_ontology_entity (
    id VARCHAR(36) PRIMARY KEY,
    ontology_id VARCHAR(36) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    entity_type VARCHAR(50) DEFAULT 'MASTER',  -- MASTER/TRANSACTION/EVENT/REFERENCE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ecos_ontology_property (
    id VARCHAR(36) PRIMARY KEY,
    entity_id VARCHAR(36) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200),
    property_type VARCHAR(50),  -- STRING/NUMBER/DATE/BOOLEAN/REFERENCE
    required BOOLEAN DEFAULT FALSE,
    sort_order INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ecos_ontology_relationship (
    id VARCHAR(36) PRIMARY KEY,
    source_entity_id VARCHAR(36) NOT NULL,
    target_entity_id VARCHAR(36) NOT NULL,
    relationship_type VARCHAR(50),  -- ONE_TO_ONE/ONE_TO_MANY/MANY_TO_MANY
    code VARCHAR(100),
    name VARCHAR(200)
);

-- Agent（Phase 6）
CREATE TABLE IF NOT EXISTS ecos_tool_definition (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200),
    description TEXT,
    endpoint VARCHAR(500),
    method VARCHAR(10) DEFAULT 'POST',
    schema_json JSON,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);
```

---

## 三、MVP 六阶段研发计划

### Phase 1: 平台基础（1 个月）

```
c2eos 前端：                                 Databridge 后端：
├── Login 页面                                ✅ /auth/login（已有）
├── 主布局（Header + Sider + Content）        ✅ 复用 sysman 认证
├── 用户管理                                  ✅ /api/system/users
├── 角色管理                                  ✅ /api/system/roles
├── 权限管理                                  ✅ /api/system/permissions
├── 组织管理                                  ✅ /api/system/organizations
└── Home 仪表盘                               新建 /api/ecos/dashboard
```

**后端工作量**：🟢 低（90% 复用，仅新增 Dashboard 聚合接口 + 菜单适配）

| 任务 | 负责人 | 工时 |
|------|-------|------|
| c2eos Login + Layout | 前端 | 3d |
| 用户/角色/权限页面 | 前端 | 3d |
| 组织管理页面 | 前端 | 2d |
| Dashboard 聚合 API | 后端 | 1d |
| 对接 sysman API（CORS/代理） | 后端 | 1d |
| **合计** | | **10d** |

---

### Phase 2: Ontology Studio（1 个月）

```
c2eos 前端：                                 Databridge 后端（新建模块）：
├── Domain 管理                               /api/ecos/ontology
├── Entity Designer（列表+创建+编辑+删除）     /api/ecos/ontology/{id}/entities
├── Property Designer（属性面板）              /api/ecos/ontology/entities/{id}/properties
├── Relationship Designer（关系图）            /api/ecos/ontology/entities/{id}/relationships
├── 实体关系图谱可视化（React Flow）           /api/ecos/ontology/domains
└── Ontology Explorer（对象浏览器）            
```

**后端新建文件清单**：
```
databridge-sysman/src/main/java/.../controller/
├── OntologyController.java           ← /api/ecos/ontology
├── OntologyEntityController.java     ← CRUD
├── OntologyPropertyController.java   ← CRUD
├── OntologyRelationshipController.java ← CRUD

databridge-sysman/src/main/java/.../entity/
├── EcosOntology.java
├── EcosOntologyEntity.java
├── EcosOntologyProperty.java
├── EcosOntologyRelationship.java

databridge-sysman/src/main/java/.../dao/
├── OntologyRepository.java
├── OntologyEntityRepository.java
├── OntologyPropertyRepository.java
├── OntologyRelationshipRepository.java

databridge-sysman/src/main/java/.../service/
├── OntologyService.java + impl
```

| 任务 | 工时 |
|------|------|
| 数据库建表 + 种子数据 | 0.5d |
| 4 个 Entity + 4 个 Repository | 1d |
| 4 个 Service + 4 个 Controller | 2d |
| 前端 Domain 管理 + Entity Designer | 3d |
| 前端 Property Designer | 1.5d |
| 前端 Relationship Designer + React Flow 图谱 | 3d |
| 联调 + 测试 | 2d |
| **合计** | **13d** |

---

### Phase 3: Object Runtime（1 个月）

```
c2eos 前端：                                 Databridge 后端：
├── Object Explorer（搜索+列表+详情）          /api/ecos/objects/{entityCode}
├── Object 动态表单（根据 Ontology 生成）      /api/ecos/objects/{entityCode}/{id}
├── Object CRUD                               /api/ecos/objects/{entityCode}/query
└── Object 关系查询                           /api/ecos/objects/{entityCode}/{id}/relations
```

**核心挑战**：Object Runtime 是动态的——根据 Ontology 定义动态生成 API 和表单。

**方案**：
- 后端：`ObjectController` 根据 `entityCode` 查 `ecos_ontology_entity` → 动态构建 SQL → 返回通用 `Map<String, Object>`
- 前端：根据 `GET /api/ecos/ontology/entities/{code}/schema` 返回的属性定义，动态渲染表单

| 任务 | 工时 |
|------|------|
| Object 动态查询引擎（Schema→SQL） | 3d |
| ObjectController（CRUD + 关系） | 2d |
| Schema API（前端表单生成依据） | 1d |
| 前端 Object Explorer | 2d |
| 前端 Object 动态表单 | 2d |
| 前端 Object 关系图 | 2d |
| 联调 + 测试 | 2d |
| **合计** | **14d** |

---

### Phase 4: Workflow Engine（1 个月）

```
c2eos 前端：                                 Databridge 后端：
├── Workflow Builder（流程画布）               /api/ecos/workflows
├── 流程模板管理                              /api/ecos/workflows/{id}/instances
├── 审批 Task 列表                            /api/ecos/workflows/tasks
├── Task 详情 + 审批操作                      /api/ecos/workflows/tasks/{id}/approve
└── 流程监控 Dashboard                        /api/ecos/workflows/tasks/{id}/reject
```

**方案**：MVP 用**简化状态机**，不引入 Temporal。每个 workflow 定义为一组节点+边，instance 跟踪当前节点和状态。

| 任务 | 工时 |
|------|------|
| Workflow 引擎（状态机核心） | 3d |
| WorkflowController + TaskController | 2d |
| 前端 Workflow Builder（React Flow） | 4d |
| 前端 Task 中心 | 2d |
| 前端 流程监控 | 1d |
| 联调 + 测试 | 2d |
| **合计** | **14d** |

---

### Phase 5: Knowledge Center（1 个月）

```
c2eos 前端：                                 Databridge 后端：
├── 文档管理（上传/列表/详情）                 /api/ecos/knowledge/documents
├── 知识搜索（关键词+语义）                    /api/ecos/knowledge/search
├── RAG 问答                                  /api/ecos/knowledge/ask
├── 知识图谱浏览                              /api/ecos/knowledge/graph
└── 向量检索状态                              /api/ecos/knowledge/vectors
```

**方案**：复用已有知识库能力，产品化封装。RAG 后端对接 hermes-engine。

| 任务 | 工时 |
|------|------|
| 文档 CRUD + 文件管理 | 2d |
| 向量化 Pipeline（文档→Embedding→VectorDB） | 3d |
| RAG 检索接口（对接 hermes-engine） | 2d |
| 前端 文档管理 | 2d |
| 前端 知识搜索 | 2d |
| 前端 RAG 问答 | 2d |
| 联调 + 测试 | 2d |
| **合计** | **15d** |

---

### Phase 6: Agent Runtime + Mission Dashboard（1 个月）

```
c2eos 前端：                                 Databridge 后端：
├── Agent Registry（列表+详情）               ✅ /api/v1/agent/profiles（已有）
├── Agent Builder（创建+配置+测试）            /api/ecos/agent/tools
├── Tool Builder（工具注册+Schema）           /api/ecos/agent/{id}/test
├── Agent 对话测试                             /api/ecos/agent/{id}/execute
├── Agent 执行记录                            /api/ecos/agent/executions
├── Mission Dashboard（任务+Agent 状态）       /api/ecos/mission/status
└── Agent 监控                                /api/ecos/mission/agents
```

**方案**：扩展已有 AgentProfile CRUD，增加 Tool 管理、测试执行、Mission Dashboard。

| 任务 | 工时 |
|------|------|
| ToolController（CRUD + Schema 管理） | 1d |
| Agent 测试/执行接口 | 1d |
| Mission Dashboard API | 1d |
| 前端 Agent Registry | 2d |
| 前端 Agent Builder（含配置+测试） | 3d |
| 前端 Tool Builder | 2d |
| 前端 Mission Dashboard | 2d |
| 联调 + 测试 | 2d |
| **合计** | **14d** |

---

## 四、总工时与人天

| Phase | 名称 | 后端工时 | 前端工时 | 合计 | 里程碑 |
|-------|------|---------|---------|------|--------|
| 1 | 平台基础 | 2d | 8d | 10d | M1: 平台运行 |
| 2 | Ontology Studio | 3.5d | 7.5d | 13d | M2: Ontology 可用 |
| 3 | Object Runtime | 6d | 6d | 14d | M3: 对象CRUD 可用 |
| 4 | Workflow | 5d | 7d | 14d | M4: 流程可用 |
| 5 | Knowledge | 7d | 6d | 15d | M5: 知识库可用 |
| 6 | Agent + Mission | 3d | 9d | 14d | M6: Agent 就绪 |
| **合计** | | **26.5d** | **43.5d** | **80d** | |

> **人天 = 1人×1天**。如果前端2人+后端2人并行，**实际工期约4个月**（而非设计文档的6个月）。

---

## 五、技术决策速查

| 决策点 | 选择 | 原因 |
|-------|------|------|
| 后端框架 | Spring Boot（复用 sysman-boot） | 已有认证/IAM/AgentProfile，零启动成本 |
| 数据库 | 扩展 sys-man schema，加 ecos_* 前缀表 | 不与现有 td_* 表冲突，便于区分 |
| Ontology 存储 | PostgreSQL JSONB 列（不引入 Neo4j 到 MVP） | MVP 规模可控，V1 再迁移到 Neo4j |
| Workflow 引擎 | 自建状态机（不引入 Temporal） | MVP 需求简单，避免外部依赖复杂性 |
| 知识图谱 | PostgreSQL 关系表 + 前端 React Flow 可视化 | MVP 不建真正 KG，用关系表模拟 |
| RAG | 复用 hermes-engine + 现有向量存储 | 已有成熟能力 |
| Agent Runtime | 复用 hermes-engine（DeepSeek） | 已有 LLM 网关 + Agent 调度 |

---

## 六、c2eos 前端工程对接清单

由于 GitHub 无法访问，这是基于设计文档技术栈（Next.js 15 + React 19 + TypeScript + TailwindCSS）的对接指南：

### c2eos 需要实现的前端模块

| 模块 | 页面数 | 关键组件 | 后端 API |
|------|-------|---------|---------|
| Auth | 2 | Login, MFA | `/auth/*` |
| Layout | 1 | Header + Sider + Content | `/api/system/menus/tree` |
| IAM | 4 | User/Role/Permission/Org CRUD | `/api/system/users`, `/roles`, etc. |
| Ontology | 5 | Domain, Entity, Property, Relationship, Explorer | `/api/ecos/ontology/**` |
| Object | 2 | Explorer + Dynamic Form | `/api/ecos/objects/**` |
| Workflow | 3 | Builder, TaskList, Monitor | `/api/ecos/workflows/**` |
| Knowledge | 3 | Documents, Search, RAG | `/api/ecos/knowledge/**` |
| Agent | 3 | Registry, Builder, Tools | `/api/ecos/agent/**` |
| Mission | 1 | Dashboard | `/api/ecos/mission/**` |

### c2eos 需要配置的 Vite/Next.js 代理

```typescript
// next.config.js 或 vite.config.ts
{
  '/api': 'http://localhost:8081/sys-man',  // sysman 后端
  '/auth': 'http://localhost:8081/sys-man',
  '/ecos-api': 'http://localhost:8081/sys-man/api/ecos',  // ECOS 新模块
}
```

---

## 七、立即执行的第一步

| 优先级 | 任务 | 产出 |
|-------|------|------|
| P0 | 在 `sys-man` 数据库建 `ecos_ontology_*` 表 | DDL 执行 |
| P0 | 创建 `OntologyController` + `OntologyService` | Java 源码 + 编译通过 |
| P0 | 等 c2eos 代码拿到后，建立第一个页面（Login + Layout） | 前端可运行 |
| P1 | Entity/Property/Relationship 三个 Controller | 后端 CRUD 完工 |
| P1 | 种子数据：2个 Domain + 5个 Entity + 15个 Property | 可演示 |

---

## 八、风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| c2eos 技术栈与设计文档不一致 | 中 | 高 | GitHub 恢复访问后立即验证 |
| Object Runtime 动态查询引擎复杂度超标 | 中 | 中 | MVP 先支持 5 个硬编码 Entity，Phase 2 再动态化 |
| Neo4j 未引入导致 KG 可视化受限 | 低 | 低 | MVP 用关系表 + 前端伪图谱，V1 迁移 |
| Databridge 团队不熟悉 Next.js | 高 | 低 | 后端团队专注 Java API，前端由 c2eos 独立推进 |
