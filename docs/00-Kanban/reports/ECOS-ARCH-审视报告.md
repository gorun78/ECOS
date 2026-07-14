# ECOS-ARCH 架构审视报告

> **日期**: 2026-06-16  
> **审视范围**: ecos系统设计 01~18 + databridge-v2 后端 + c2eos 前端  
> **审视角色**: ECOS-ARCH（首席架构师）  
> **等级说明**: 🔴 严重 | 🟡 警告 | 🟢 合规  

---

## 1. 设计与实现差距

### 1.1 核心架构原则对齐度

| 架构原则（来自 HLD/DDD） | 设计要求 | 实际实现 | 差距等级 |
|---|---|---|---|
| **Domain Driven** | 按 Bounded Context 拆分（Identity/Ontology/Agent/Knowledge Context） | 后端按 datanet/runtime/sysman 等模块拆分，但未严格遵循 DDD 的 Aggregate/Repository 模式 | 🟡 |
| **Ontology First** | 所有业务对象必须来自 Ontology，通过 `OntologyObject` 统一抽象 | Ontology 模块存在，但部分业务仍直接操作数据库 Entity（`td_` 表） | 🟡 |
| **Event Driven** | 服务间通信优先事件（Kafka Domain Event / Outbox Pattern） | **未实现**。完全无 Kafka 集成，服务间为同步 HTTP 调用 | 🔴 |
| **AI Native** | 所有模块支持 Agent/Tool/语义检索调用 | Agent Runtime 已实现 ReAct 模式，但其他模块未提供标准化 Tool 接口 | 🟡 |
| **Database Per Service** | 每个微服务一个独立 Schema，禁止跨服务写库 | 多个模块共享同库（MySQL），未实现独立 Schema 隔离 | 🔴 |
| **API First** | OpenAPI Contract First 开发流程 | 无 OpenAPI 规范文档，Controller 直接实现 | 🔴 |

### 1.2 分层架构对齐度

| 设计层（DIKW） | 设计规格 | 实现状态 | 差距详情 |
|---|---|---|---|
| **Data Platform** | catalog-service / lineage-service / connector-service / pipeline-service | ✅ datanet 模块实现了 Catalog 基础功能，有搜索/注册/stats 端点 | 缺少 lineage（血缘）和 pipeline（ETL）服务 |
| **Semantic Platform** | ontology-service / object-service / query-service / metric-service | 🟡 Ontology 和 Object 基础 CRUD 已实现 | 缺少 metric-service；未实现 GraphQL 聚合查询 |
| **Operations Platform** | workflow-service / case-service / action-service / rule-service | 🟡 Workflow 模块部分存在（5 表 15 文件） | 未对接前端，无 Case 管理，无 Rules Engine |
| **Agent Platform** | agent-studio / prompt-service / tool-service / agent-runtime / memory-service / policy-service | 🟡 AgentRuntime 已实现 ReAct 模式 | 缺少 Memory Service、Policy Service、Prompt 版本管理 |
| **Knowledge Platform** | knowledge-service / graph-service / memory-service | 🟡 Knowledge 模块部分存在（3 表 10 文件） | 使用 MySQL 存储图谱 → 无 Path Query / Graph RAG 能力 |
| **Cognitive Platform** | World Model / Simulation / Optimization | 🔴 **未实现** | 完全无代码 |
| **Mission Control** | goal-service / kpi-service / dashboard-service | 🔴 **未实现** | 完全无代码 |

### 1.3 API 设计对齐度

| 维度 | 设计规范（API Design Spec） | 实际实现 | 差距等级 |
|---|---|---|---|
| 统一前缀 | `/api/v1/{resource}` | 混杂：`/datanet/catalog/` `/api/v1/ecos/objects/` `/api/agent/` | 🔴 |
| 返回结构 | `{ success, code, message, data }` （string code） | `{ code, message, data, timestamp }`（int code, 0=success） | 🟡 |
| 错误码体系 | `DOMAIN_ERRORCODE`（如 `OBJECT_NOT_FOUND`） | 使用 HTTP 状态码映射：400/401/403/404/-1 | 🟡 |
| 分页格式 | 标准 `{ pageNo, pageSize, total }` | Controller 内嵌 Map 返回，无统一分页模型 | 🟡 |
| GraphQL | `POST /graphql` 统一入口 | **未实现** | 🔴 |
| Resource Naming | `/api/v1/customers/123` RESTful | 存在 `POST /api/v1/ecos/objects/{entity}` 等混合风格 | 🟡 |

### 1.4 数据库设计对齐度

| 维度 | 设计规格 | 实际实现 | 差距等级 |
|---|---|---|---|
| 数据库类型 | PostgreSQL | MySQL | 🔴 |
| 表命名 | `{domain}_{entity}`（如 `identity_user`） | 遗留 `td_` 前缀（如 `td_user`, `td_role`） | 🔴 |
| 主键 | UUID | UUID（✅ 一致） | 🟢 |
| Polyglot | PostgreSQL + ClickHouse + Neo4j + Redis + ES + Vector DB | 仅 MySQL | 🔴 |
| Schema 分离 | 独立 schema（`ecos_identity`、`ecos_ontology`...） | 统一数据库，无 Schema 隔离 | 🔴 |

### 1.5 数据流架构对齐度

| 数据通路 | 设计规格 | 实际实现 | 差距等级 |
|---|---|---|---|
| 服务间通信 | Event (Kafka) | Sync HTTP (REST) | 🔴 |
| 分布式事务 | Saga / Outbox Pattern | 无分布式事务处理 | 🔴 |
| CDC | Kafka Connect CDC | 未实现 | 🔴 |
| 实时推送 | WebSocket | 未实现（仅备注"已有 FastAPI bridge 模板"） | 🔴 |

---

## 2. 技术债务

### 2.1 后端债务

| 模块 | 问题 | 影响 | 建议修复方案 |
|---|---|---|---|
| **所有模块** | 遗留表命名 `td_` 前缀 | 与设计文档完全不匹配，新开发者困惑 | 迁移至 `{domain}_{entity}` 命名，建立视图兼容层 |
| **所有模块** | MySQL 而非 PostgreSQL | 设计假定 PG 特性（JSONB、数组、GIN 索引）不可用 | 数据迁移至 PostgreSQL，或调整设计接受 MySQL |
| **databridge-aimod** | 空模块（无源码） | 无 AI 模块化能力 | 确认是否需要，清理或实现 |
| **databridge-buszhi** | 空模块（无源码） | 业务智能模块缺失 | 确认是否需要，清理或实现 |
| **databridge-dccheng** | 空模块（无源码） | 代码已归档但无源码 | 清理空目录 |
| **databridge-gateway** | 无 Java 源码 | 无网关路由逻辑，入口不明 | 实现 Spring Cloud Gateway 或移除 |
| **所有 Controller** | API 路径不一致（`/datanet/catalog/` vs `/api/v1/ecos/objects/`） | 前端 BFF 需额外翻译路径，增加维护成本 | 统一到 `/api/v1/{domain}/{resource}` |
| **CatalogController** | `countAllFields()` 全表分页遍历 | O(n) 性能，大数据量不可用 | 改为 SQL `COUNT`/`SUM` 聚合查询 |
| **AgentRuntimeImpl** | `parseArguments` 手写 JSON 解析 | 易出错，不支持复杂嵌套 JSON | 替换为 Jackson 标准反序列化 |
| **所有模块** | 无单元测试 | 无法保证回归质量 | 按测试规范（文档17）建 JUnit5 + Mockito 测试 |
| **所有模块** | 无 OpenAPI/Swagger 文档 | 前端对接需读源码 | 集成 springdoc-openapi 生成 |
| **seed.sql** | 数据量少（仅 3 角色 1 用户 11 权限） | Demo 展示数据不足 | 扩充种子数据到 8-10 条，增加业务场景 |

### 2.2 前端债务

| 模块 | 问题 | 影响 | 建议修复方案 |
|---|---|---|---|
| **api.ts** | Mock 数据为默认回退，后端为 try/catch 降级 | 隐藏后端不可用状态，开发期难以发现集成问题 | 环境变量控制：开发环境 mock，生产环境必须后端 |
| **所有页面** | 10 张页面仅 5 张对接真实 API | 演示效果受限 | 按 P1 计划补齐 API 对接 |
| **前端** | 无认证集成 | 无法对接 sysman IAM | 集成 JWT token 管理和登录页 |
| **前端** | 无全局 Loading/Error 状态统一管理 | 用户体验不一致 | 实现统一的请求状态管理（React Query/SWR） |
| **前端** | 服务器端 BFF（server.ts）与后端路径翻译 | 额外一层增加复杂度 | 统一前后端 API 路径后消除 BFF |

### 2.3 架构级债务

| 问题 | 影响 | 严重度 |
|---|---|---|
| 无事件总线（Kafka/Event Bus） | 无法实现异步解耦、无法支撑 Agent Mesh 通信 | 🔴 |
| 无 Service Mesh（Istio） | 无法实现 mTLS、灰度发布、分布式追踪 | 🟡 |
| 无独立配置中心（Nacos/Consul） | 配置散落在各个模块 | 🟡 |
| 无统一日志收集（ELK/Loki） | 排障困难 | 🟡 |
| 无 CI/CD Pipeline | 手动部署，无法保证发布质量 | 🔴 |
| 无性能测试 | 99.95% SLA 无保障 | 🔴 |
| 无安全扫描/渗透测试 | 等保三级合规无保障 | 🔴 |

---

## 3. 架构健康度

### 3.1 模块耦合度分析

```
当前依赖关系（已实现）：
  datanet ──→ common
  runtime ──→ common
  sysman  ──→ common
  (模块间无直接依赖，全部通过 common 共享)

理想依赖关系（设计）：
  UI → API Gateway → Identity → Catalog → Ontology → Object → Workflow → Agent → Knowledge → World Model
  (数据流：Data → Semantic → Agent → Knowledge → Cognitive)
```

**评估**: 当前模块间耦合度较低（仅 shared common），但代价是模块间缺乏定义明确的接口契约。模块间调用为直接 HTTP，缺少熔断/降级/限流。

### 3.2 API 设计一致性评分

| 维度 | 评分（1-5） | 说明 |
|---|---|---|
| 路径一致性 | ⭐⭐ | 混合 `/datanet/catalog/` 和 `/api/v1/ecos/objects/` 等多种风格 |
| 返回格式统一 | ⭐⭐⭐ | ApiResponse 基础格式统一，但错误码体系不一致 |
| RESTful 合规 | ⭐⭐ | 存在动词式路径（`/search` `/register` `/dashboard`） |
| 版本管理 | ⭐ | 无版本管理机制 |
| 文档化 | ⭐ | 无 OpenAPI 文档 |

### 3.3 可扩展性评估

| 维度 | 现状 | 评估 |
|---|---|---|
| 水平扩展 | 无 K8s 部署，无 HPA | 当前单机部署无法水平扩展 |
| 服务拆分 | 7 个 Maven 模块，但运行时未独立部署 | 未实现真正的微服务 |
| 数据库扩展 | 单 MySQL 实例 | 无法支撑 PB 级数据 |
| 缓存 | 无 Redis 集成 | 高并发场景无法保障 |
| 异步处理 | 无消息队列 | 同步阻塞降低吞吐 |

### 3.4 性能瓶颈识别

| 瓶颈 | 位置 | 说明 |
|---|---|---|
| Catalog 全表遍历 | `CatalogController.countAllFields()` | 分页遍历所有数据做聚合，O(n) 复杂度 |
| JSON 解析 | `AgentRuntimeImpl.parseArguments()` | 手写解析器，性能差且功能不全 |
| MySQL 单库 | 所有模块共享 | 无读写分离，无分片 |
| 同步 REST | 全部服务间调用 | 串行阻塞，Agent Mesh 多 Agent 协作时延迟累积 |

### 3.5 安全与合规评估

| 需求（安全架构设计 14） | 实现状态 | 差距 |
|---|---|---|
| Zero Trust | ❌ 未实现 | 无服务间 mTLS，无请求级持续验证 |
| IAM（多租户/用户/角色） | ✅ 部分实现（sysman） | 以 RBAC 为主，ABAC 有模型定义但未集成到业务层 |
| JWT + OIDC | 🟡 部分实现 | JWT 存在但未对接 OIDC Provider |
| MFA | ❌ 未实现 | 无多因素认证 |
| Audit | ✅ 部分实现 | 审计日志模型存在，但前端未深度集成 |
| 等保三级 / 四级 | ❌ 未实现 | 无等保合规检查，无安全扫描 |
| 字段级权限控制 | ❌ 未实现 | 设计要求的 Entity/Object/Field 三级控制未实现 |

---

## 4. 下一阶段架构建议

### 4.1 立即行动（P0 - 2周内）

| # | 行动 | 原因 | 负责人建议 |
|---|---|---|---|
| 1 | **统一 API 路径规范**：将所有 Controller 路径改为 `/api/v1/{domain}/{resource}` | 前端/后端路径混乱是最大开发效率障碍 | 后端团队 |
| 2 | **消除空的 Maven Module**：清理 aimod/buszhi/dccheng | 减少构建混乱和认知负担 | 架构师 |
| 3 | **修复 Catalog 全表遍历性能问题** | 生产环境不可用 | 后端团队 |
| 4 | **建立 CI Pipeline**（至少编译+单元测试） | 无 CI 无法保障代码质量 | DevOps 团队 |

### 4.2 短期（P1 - 1个月内）

| # | 行动 | 优先级 |
|---|---|---|
| 1 | **数据库迁移计划**：MySQL → PostgreSQL + Schema 隔离 | 🔴 高 — 设计文档全部基于 PG |
| 2 | **表名重构**：`td_*` → `{domain}_{entity}` | 🔴 高 — 消除 legacy 债务 |
| 3 | **集成 Kafka**：先作为 Agent Mesh 通信总线 | 🔴 高 — Agent Mesh 文档要求 |
| 4 | **补齐 Ontology + Object 模块**：实现 Ontology DSL 编译器 | 🟡 中 — 核心差异化能力 |
| 5 | **OpenAPI 文档自动生成** | 🟡 中 — 提升前后端协作效率 |
| 6 | **实现统一分页+错误码规范** | 🟡 中 — API 一致性 |

### 4.3 中期（P2 - 3个月内）

| # | 行动 | 说明 |
|---|---|---|
| 1 | **引入 Neo4j** 替代 MySQL 存储知识图谱 | 实现设计要求的 Knowledge Graph Path Query |
| 2 | **实现 Agent Mesh 多 Agent 协作** | 当前仅有单 Agent ReAct，需实现 Coordinator/Discovery |
| 3 | **引入 Istio Service Mesh** | mTLS、灰度、追踪 |
| 4 | **World Model 启动设计** | 因果图 + 仿真基础能力 |
| 5 | **实现 Redis 缓存层** | Session 管理 + 热点数据缓存 |
| 6 | **前端认证集成** | 对接 sysman JWT + OIDC |

### 4.4 架构重构建议：分两阶段

**阶段一（1-2月）：合规治理**
```
目标：让实现与设计文档对齐
1. API 路径统一     → /api/v1/{domain}/{resource}
2. 数据库迁移       → PostgreSQL + Schema 隔离
3. 表命名重构       → {domain}_{entity}
4. 响应格式统一     → 设计规范的 {success, code, message, data}
5. 引入 Kafka       → 作为事件总线
6. 补齐空模块       → 确认/清理/实现
```

**阶段二（3-6月）：能力提升**
```
目标：实现缺失的 DIKW 上层能力
1. Knowledge Graph  → 迁移到 Neo4j + Graph RAG
2. Agent Mesh       → 多 Agent 协作 + Coordinator
3. World Model      → 因果模型 + 初始仿真
4. Mission Control  → Goal + KPI + Dashboard
5. Polyglot DB      → ClickHouse(分析) + ES(搜索) + Vector DB(RAG)
```

### 4.5 总体架构健康评分

| 维度 | 评分 | 趋势 |
|---|---|---|
| 设计文档完整性 | ⭐⭐⭐⭐ | 文档 01-18 覆盖全面，但偏咨询级/概要级 |
| 实现与设计对齐度 | ⭐⭐ | 核心原则（DDD/Ontology First/Event Driven）严重偏离 |
| 代码质量 | ⭐⭐⭐ | ApiResponse 等基础设施良好，但残留手写解析和全表遍历 |
| 可测试性 | ⭐ | 无单元测试，不可测试 |
| 可部署性 | ⭐ | 无 CI/CD，无容器化，无编排 |
| 安全性 | ⭐⭐ | 基础 IAM 存在，但 Zero Trust/MFA/等保缺失 |
| 可扩展性 | ⭐ | 单 MySQL + 同步调用，无法水平扩展 |
| **综合健康度** | **⭐⭐（Low）** | 需系统性架构治理 |

---

## 5. 总结

### 最大风险 TOP 5

1. **数据库选型偏差**：设计文档全部基于 PostgreSQL 特性设计，实际使用 MySQL 已产生严重阻抗失配
2. **事件驱动缺失**：无 Kafka/消息队列，无法支撑 Agent Mesh、Workflow 异步、CDC 等核心架构特性
3. **API 路径混乱**：3+ 种路径风格，前端 BFF 翻译层增加复杂度
4. **空模块残留**：3 个 Maven 模块无代码，构建系统含"幽灵"模块
5. **测试与 CI 空白**：无单元测试、无 CI/CD，无法保证发布质量

### 最大优势 TOP 3

1. **设计文档基础扎实**：01-18 号文档在架构理念和愿景上达到了 Foundry 级别水准
2. **Agent Runtime 实现质量较好**：ReAct 模式实现清晰，Session 管理完善
3. **前端页面覆盖面广**：18+ 页面组件，Palantir 风格 UI，展示了完整的平台愿景

### 下一步行动

建议组织一次架构治理会议，确认以下决策：
1. 是否接受 MySQL 作为正式数据库（放弃 PostgreSQL 计划）？
2. 是否统一 API 路径规范（接受短期重构成本）？
3. 是否引入 Kafka 作为事件总线（接受运维复杂度增加）？
4. 空模块的去留决策？

> 本报告由 ECOS-ARCH 于 2026-06-16 自动生成，基于设计文档（01-18）与现有实现（databridge-v2 + c2eos）的对比分析。
