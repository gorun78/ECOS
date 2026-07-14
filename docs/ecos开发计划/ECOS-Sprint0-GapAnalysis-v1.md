# ECOS Gap Analysis Report — Sprint 0 Planning

> **Generated**: 2026-06-17  
> **Scope**: Design docs vs current implementation (backend + frontend + database)  
> **Methodology**: Systematic review of all 17 design docs, 20 functional design modules, MVP v3 plan, backend Java code, frontend TSX/TS pages, and SQL migrations

---

## 一、Executive Summary

### Overall Maturity: ~35% of Full Design → ~60% of MVP Scope

| Layer | Design Coverage | Implementation | Status |
|-------|----------------|----------------|--------|
| **Data Platform** | Full (4 modules) | Partial (Data Catalog + Quality) | 🔶 30% |
| **Semantic Platform** | Full (4 modules) | Significant (Ontology + Object Runtime + Query) | 🔵 75% |
| **Agent Platform** | Full (4 modules) | Significant (Agent Runtime + Mesh + Config) | 🔵 80% |
| **Knowledge Platform** | Full (2 modules) | Partial (KG base, no Neo4j) | 🔶 40% |
| **Cognitive Platform** | Full (3 modules) | Minimal (World Model stub, no Autonomous Engine) | 🔴 15% |
| **App/Portal** | Full (2 modules) | Minimal (single COG page, no Low-Code) | 🔴 10% |
| **Cross-Cutting** | Full (IAM, Security, DevOps) | Partial (IAM basic, Audit base, no SSO/MFA) | 🔶 50% |

---

## 二、Module-by-Module Gap Analysis

### Module 1: Identity & IAM (模块1)
**Design Reference**: 18-MVP路线图 §4.1, 14-安全架构设计说明书  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| User CRUD | Full management | ✅ UserDao, UserController, UserAccount entity | None | — |
| Role/Permission | RBAC + ABAC | ✅ RoleDao, RolePermission, AbacPolicy, DataPermission | None | — |
| Organization | Org hierarchy | ✅ Organization entity, OrganizationController | None | — |
| JWT Auth | Token + refresh | ✅ AuthController with access/refresh tokens | None | — |
| SSO | Single Sign-On integration | ❌ Not implemented | Full SSO missing | 🟡 MEDIUM |
| MFA | Multi-factor auth | ❌ Not implemented | Full MFA missing | 🟡 MEDIUM |
| Data permission filtering | Row/Column level | ⚠️ ABAC framework + DataPermissionInterceptor exist but not wired to all controllers | Partial integration | 🟡 MEDIUM |
| Operation audit | All CUD audit logs | ⚠️ AuditLogDao exists, AuditController present | Not fully wired | 🟢 LOW |

**Recommendation**: Deploy to Sprint 0: wire audit to all controllers, validate data permission integration. SSO/MFA defer to V1.

---

### Module 2: Ontology Studio (模块2 / 1026)
**Design Reference**: 07-Ontology元模型设计说明书, 1026-第6章 Ontology平台  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Domain management | Domain CRUD | ❌ Not implemented | Full gap | 🔴 HIGH |
| Entity Designer | Entity create/edit with attributes | ✅ OntologyController Entity CRUD, V3 migration | None | — |
| Property Designer | Property panel (type/validation/default) | ✅ OntologyController Property CRUD | None | — |
| Relationship Designer | Visual drag-and-drop canvas (React Flow) | ✅ Backend CRUD; ❌ Frontend has listing only, no React Flow canvas | Visual designer missing | 🟡 MEDIUM |
| Action Designer | Action definition (type/rule/strategy) | ❌ Not implemented | Full gap | 🔴 HIGH |
| Rule Designer | 4 rule types | ❌ Not implemented | Full gap | 🔴 HIGH |
| Ontology Explorer | Entity relationship graph | ✅ Frontend OntologyExplorer exists | Basic working | 🟢 LOW |
| Version management | Publish/rollback | ❌ Not implemented | Full gap | 🔴 HIGH |
| Ontology→DB sync | Auto-sync schema | ❌ Not implemented | Full gap | 🔴 HIGH |
| Visual entity graph | React Flow drag-drop | ❌ No visual canvas on OntologyDesigner | Gap | 🟡 MEDIUM |

**Recommendation**: P0-2 scope (as per v3 plan). Sprint 0: Action Designer + Ontology Explorer enhancement. Version management and schema sync defer to P1.

---

### Module 3: Object Runtime (模块3 / 1027)
**Design Reference**: 1027第7章 对象运行时  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Object CRUD | Create/Read/Update/Delete | ✅ ObjectController full CRUD via JDBC | None | — |
| Dynamic schema | Schema from Ontology | ✅ Schema from information_schema columns | Basic, OK for MVP | 🟢 LOW |
| Status machine | State transitions (Draft→Active→Archived) | ✅ Status change endpoint | No state transition validation | 🟢 LOW |
| Relationship browsing | Relationship graph visualization | ⚠️ Returns empty `relations: []` | Needs real data | 🟡 MEDIUM |
| Object Timeline | Operation log timeline | ⚠️ Returns empty `timeline: []` | Needs audit log integration | 🟡 MEDIUM |
| Object search (keyword) | LIKE search across entities | ✅ `/search` endpoint with ILIKE | Basic only | 🟢 LOW |
| Object search (semantic) | Embedding-based semantic search | ❌ Not implemented | Full gap | 🟡 MEDIUM |
| Dynamic form generation | Form from Ontology property defs | ✅ Frontend basic, not fully dynamic | Enhancement | 🟢 LOW |
| Object permission | Row-level data security | ⚠️ ABAC framework exists, not wired to ObjectController | Not integrated | 🟡 MEDIUM |

**Recommendation**: Sprint 0: wire real relations data from Ontology relationships, connect timeline to audit log. Semantic search defer to P1.

---

### Module 4: Workflow Engine (模块4 / 1029)
**Design Reference**: 1029第9章 工作流与流程自动化  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Workflow CRUD | Create/Read/Update/Delete | ✅ WorkflowController, WorkflowService, V2 migration | None | — |
| Visual designer | React Flow drag-and-drop | ✅ WorkflowDesigner.tsx (664 lines, 5 node types) | Full working | 🟢 LOW |
| Node types (5) | Start/End/Human/Agent/Condition | ✅ All 5 types implemented | None | — |
| Condition gateway | Condition expression editor | ❌ Not implemented | Full gap | 🔴 HIGH |
| Parallel branches | AND-split / AND-join | ⚠️ Backend has `mode: parallel` but no real engine extension | Partial | 🟡 MEDIUM |
| Agent node | Agent selection + input/output mapping | ✅ Agent node type in designer | Basic working | 🟢 LOW |
| Workflow execution | Real state machine execution | ⚠️ `publish` sets status, `test` returns mock | Mock execution | 🟡 MEDIUM |
| Workflow test | Simulation + step visualization | ⚠️ Mock response with hardcoded steps | Needs real engine | 🔴 HIGH |
| Temporal integration | Temporal workflow engine | ❌ Self-built state machine, not Temporal | Architecture deviation | 🟡 MEDIUM |

**Recommendation**: Sprint 0: implement condition expression editor and real execution engine (connect to existing state machine). Temporal defer to V1.

---

### Module 5: Knowledge Center (模块5 / 10213)
**Design Reference**: 10-Knowledge Graph设计说明书, 10213第13章知识图谱平台  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Document management | Upload/classify/search | ❌ Not implemented | Full gap | 🔴 HIGH |
| RAG pipeline | Retrieve + Generate | ⚠️ BGE vector search, HermesEngine execution | Basic | 🟡 MEDIUM |
| Knowledge search | Full-text + semantic | ✅ BGE-small-zh-v1.5, PostgreSQL vector search | Working | 🟢 LOW |
| Graph storage (Neo4j) | Neo4j native graph | ❌ PostgreSQL JSONB instead of Neo4j | Architecture gap | 🔴 HIGH |
| Entity-relation extraction | Agent auto-identify entities→graph | ❌ Not implemented | Full gap | 🔴 HIGH |
| Graph Explorer | React Flow visualization | ✅ KnowledgeGraphController + frontend connector | On PG data, not Neo4j | 🟡 MEDIUM |
| Graph search | Node/relation/path search | ✅ search endpoint | Basic only | 🟢 LOW |
| Path analysis | Shortest/all paths between 2 nodes | ❌ Not implemented | Full gap | 🟡 MEDIUM |
| Graph RAG | Subgraph→LLM context→answer | ❌ Not implemented | Full gap | 🔴 HIGH |

**Recommendation**: P1-2 scope. Sprint 0: document management basic CRUD + path analysis endpoint. Neo4j and Graph RAG defer to P1.

---

### Module 6: Agent Runtime (模块6 / 10211)
**Design Reference**: 08-Agent Runtime设计说明书, 10211第11章智能体运行时  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| ReAct engine | Goal→Plan→Execute→Evaluate→Learn | ✅ HermesEngineImpl with ReAct pattern | Working | 🟢 LOW |
| LLM Gateway | Multi-model (DeepSeek/Qwen/Claude/GPT) | ✅ LLMGatewayImpl, DeepSeek v4-flash | Single provider | 🟢 LOW |
| Tool framework | Tool registry + tool execution | ✅ ToolRegistry, 4+ tools | Working | 🟢 LOW |
| 3-layer memory | Session/Episodic/Semantic | ✅ SessionManager, AgentSession | Basic | 🟢 LOW |
| Agent profile | Configurable agent profiles | ✅ AgentConfigService, ecos_agent table, V4 migration | None | — |
| Agent Builder UI | Visual create/configure/test | ✅ AgentConfigController, AgentStudio.tsx | Frontend connected | 🟢 LOW |
| Tool binding | Visual tool selector | ✅ PUT /api/v1/agents/{id}/tools | Working | 🟢 LOW |
| Knowledge binding | Knowledge base selector | ✅ PUT /api/v1/agents/{id}/knowledge | Working | 🟢 LOW |
| Prompt versioning | Version diff/rollback | ⚠️ List exists, no diff/rollback | Partial | 🟡 MEDIUM |
| Agent test console | Chat test panel | ⚠️ Test endpoint returns mock | Needs real execution | 🟡 MEDIUM |
| Agent call metrics | Success/error tracking | ✅ AgentMetrics, AgentCallLog | Working | 🟢 LOW |
| Agent Marketplace | Publish/subscribe/rate | ❌ Not implemented | Full gap | 🔴 HIGH |
| Agent Reputation | Trust scoring | ❌ Not implemented | Full gap | 🔴 HIGH |

**Recommendation**: Sprint 0: implement real test execution, prompt version diff/rollback. Marketplace + Reputation defer to P2.

---

### Module 7: Agent Mesh (模块7 / 10212)
**Design Reference**: 09-Agent Mesh设计说明书, 10212第12章智能体网络  
**MVP Priority**: P1 (but largely ✅)

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Supervisor mode | Central orchestrator | ✅ AgentMeshController, MissionExecutionEngine | Working | 🟢 LOW |
| Pipeline mode | Sequential agent chain | ✅ Pipeline mode with task decomposition | Working | 🟢 LOW |
| A2A MessageBus | Agent-to-agent communication | ✅ AgentMessageBus | Basic | 🟢 LOW |
| Agent registry | Agent CRUD + status | ✅ AgentRegistryRepository + V4 DB | None | — |
| Mission management | Mission CRUD + execute | ✅ MissionRepository, V4 DB | None | — |
| Task tracking | Per-task status | ✅ MissionTaskRepository | None | — |
| Frontend UI | Agent panel + Mission workbench | ✅ AgentMesh.tsx (341 lines) | Working | 🟢 LOW |
| A2A Protocol | Standardized agent protocol | ⚠️ Basic, not full A2A spec | Needs extension | 🟡 MEDIUM |
| Supervisor selection algorithm | Smart agent routing | ❌ Not implemented | Full gap | 🟡 MEDIUM |

**Recommendation**: Agent Mesh is in good shape. Sprint 0: basic supervisor selection logic (e.g., round-robin). Full A2A protocol defer to V1.

---

### Module 8: World Model (模块8 / 10214)
**Design Reference**: 11-World Model设计说明书, 10214世界模型平台  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Goals CRUD | Goal hierarchy + progress tracking | ⚠️ ConcurrentHashMap, seeded data | **No DB persistence** | 🔴 HIGH |
| Goal tree | Parent-child goal hierarchy | ✅ Goal tree endpoint | Working but in-memory | 🟡 MEDIUM |
| CausalLinks | Cause→Effect relationships | ⚠️ ConcurrentHashMap, seeded data | **No DB persistence** | 🔴 HIGH |
| Causal graph | Causal relationship visualization | ✅ Causal graph endpoint | Working but in-memory | 🟡 MEDIUM |
| Scenarios | Scenario creation + parameter config | ⚠️ ConcurrentHashMap, seeded data | **No DB persistence** | 🔴 HIGH |
| Scenario comparison | What-If analysis | ✅ Compare endpoint | Mock only | 🟡 MEDIUM |
| Frontend visualization | Goal tree + causal graph + scenario cards | ✅ WorldModelViewer.tsx (367 lines) | Working | 🟢 LOW |
| What-If analysis | Modify params → recalculate → diff | ❌ Not implemented | Full gap | 🔴 HIGH |
| Scenario simulation | Execute scenario → result | ❌ Not implemented | Full gap | 🔴 HIGH |

**Recommendation**: P0-4 scope. **Critical gap**: all data is in-memory, will be lost on restart. Sprint 0: create DB migration for goals/causal-links/scenarios tables, replace ConcurrentHashMap with JdbcTemplate. What-If analysis defer to P1.

---

### Module 9: Data Quality (模块9 / 105)
**Design Reference**: 105-第5章 数据治理与质量, 18-MVP路线图 §4.5  
**MVP Priority**: P0

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Quality rules CRUD | 5 rule types | ⚠️ ConcurrentHashMap, seeded data | **No DB persistence** | 🔴 HIGH |
| Rule execution engine | Real-time/Batch/Event-driven | ⚠️ `/check` returns mock result | **No real execution** | 🔴 HIGH |
| Quality scoring | Completeness/Accuracy/Consistency/Uniqueness/Timeliness | ⚠️ Average pass rate only | **Partial** | 🔴 HIGH |
| Issue management | Issue state machine | ⚠️ ConcurrentHashMap, seeded data | **No DB persistence** | 🔴 HIGH |
| Issue resolution | Resolve endpoint | ✅ Resolve endpoint | Working but in-memory | 🟡 MEDIUM |
| Dashboard | Score/Anomaly/Trend/Distribution | ✅ Dashboard endpoint | Working but in-memory | 🟡 MEDIUM |
| Frontend UI | Quality dashboard with tabs | ✅ DataQualityDashboard.tsx (263 lines) | Working | 🟢 LOW |

**Recommendation**: P0-5 scope. **Critical gap**: all in-memory. Sprint 0: create DB migration for quality_rules and quality_issues tables, replace all ConcurrentHashMap with JdbcTemplate. Rule execution engine defer to P1.

---

### Module 10: Unified Query & Semantic Layer (模块10 / 10210)
**Design Reference**: 10210第10章 统一查询与语义层  
**MVP Priority**: P1

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| ObjectQL engine | Parse ObjectQL→Generate SQL | ✅ ObjectQLParser, filter/sort/paginate/aggregate | Working | 🟢 LOW |
| ObjectQL API | POST /api/query/objectql | ✅ ObjectQLController | Working | 🟢 LOW |
| Semantic dictionary | "高价值客户"→Score>80 | ✅ SemanticQueryService | Basic mapping | 🟢 LOW |
| NLQ endpoints | POST /api/query/nlq | ✅ NLQController | Working | 🟢 LOW |
| Agent Runtime fallback | NLQ→Agent→ObjectQL→Result | ⚠️ Fallback returns message, not Agent Runtime | **Not implemented** | 🟡 MEDIUM |
| Query history | History list + reuse | ✅ QueryHistoryService | Working | 🟢 LOW |

**Recommendation**: Sprint 0: wire Agent Runtime fallback for NLQ (connect NLQController to HermesEngine). This is a small integration task.

---

### Module 11: Data Catalog (模块11 / 102)
**Design Reference**: 102-数据目录, 102数据平台层功能设计  
**MVP Priority**: P1

| Feature | Design Spec | Current State | Gap | Severity |
|---------|------------|---------------|-----|----------|
| Data asset browsing | Catalog listing with search | ✅ CatalogController, DataCatalog.tsx | Working | 🟢 LOW |
| Data lineage | Asset lineage visualization | ✅ Basic lineage in frontend | Working | 🟢 LOW |
| Glossary CRUD | Business glossary | ⚠️ In-memory ConcurrentHashMap | **No DB persistence** | 🔴 HIGH |
| Term-asset linking | Link glossary terms to assets | ❌ Not implemented | Full gap | 🟡 MEDIUM |
| Classification panel | Sensitivity classification display | ✅ Classification panel | Basic | 🟢 LOW |
| Agent auto-classify | Agent scans fields→suggests level | ✅ ClassificationController, HermesEngine integration | Working | 🟢 LOW |
| Data Marketplace | Browse/popular/recommended | ⚠️ Static mock data | **No DB persistence** | 🔴 HIGH |
| Access request flow | Request→Approval workflow | ⚠️ Stub endpoint, no real approval | **Not implemented** | 🔴 HIGH |

**Recommendation**: Sprint 0: create DB migrations for glossary_terms and marketplace tables. Term-asset linking and access request workflow defer to P1.

---

### Module 12-20: Not Yet Started

| Module | Design Doc | Priority | Status | Sprint 0 Action |
|--------|-----------|----------|--------|-----------------|
| **P2-1: Lakehouse (Iceberg+MinIO)** | 104第4章 数据存储与湖仓 | P2 | ❌ Not started | No action |
| **P2-2: MDM 主数据管理** | 102-数据目录 | P2 | ❌ Not started | No action |
| **P2-3: Agent Marketplace** | 10211 §11.35 | P2 | ❌ Not started | No action |
| **P2-4: Agent Reputation** | Design implied | P2 | ❌ Not started | No action |
| **P2-5: 自治企业引擎** | 10216第16章 | P2 | ❌ Not started | No action |
| **P2-6: Low-Code Builder** | 10317第17章 | P2 | ❌ Not started | No action |
| **P2-7: 统一门户** | 10320第20章 | P2 | ❌ Not started | No action |
| **10318: 企业数据编制** | 10318 | — | ❌ Not started | No action |
| **10319: 治理与安全中心** | 10319 | — | ❌ Not started | No action |

---

## 三、Cross-Cutting Architectural Gaps

| Gap | Design Spec | Current | Impact | Recommended Action |
|-----|------------|---------|--------|-------------------|
| **Kafka Event Bus** | Asynchronous event-driven architecture | Spring Event (in-process) | No cross-service event propagation | Keep Spring Event for MVP, Kafka for V1 |
| **Neo4j Graph DB** | Native graph storage for Knowledge Graph | PostgreSQL JSONB | No graph traversal, path analysis limited | P0: keep PG JSONB. P1: introduce Neo4j |
| **Temporal Workflow** | Production-grade workflow engine | Self-built state machine | No retry/compensation/timeout | Keep self-built for MVP, evaluate Temporal at V1 |
| **ClickHouse Analytics** | Real-time analytics | Not used | No analytical queries | P2 scope |
| **Elasticsearch** | Full-text search | PostgreSQL ILIKE | Search quality limited | P1 scope |
| **Redis Cache** | Distributed caching | Not used | Performance bottleneck at scale | P1 scope |
| **MinIO Object Storage** | S3-compatible storage | Not used | No file/document storage | P2 scope |
| **Microservice Split** | 5+ independent services | Monolith sysman-boot | Single deployment unit | Accept for MVP, split at V1 |
| **Multi-tenancy** | Tenant isolation | Basic TenantContext | No tenant data isolation | P1 scope |
| **SSO/OAuth2** | Single Sign-On | JWT hardcoded admin | No enterprise SSO | P0-6 scope |
| **Docker/K8s Deployment** | Container orchestration | Not deployed | No deployment automation | Sprint 0: create Dockerfile |
| **CI/CD Pipeline** | DevOps pipeline | Not implemented | No automated testing/deploy | Sprint 0: basic GitHub Actions |

---

## 四、Sprint 0 Priority Recommendations

### 🔴 Critical (Blocking): Move In-Memory Data to DB

| Task | Module | Current | Target | Estimated Effort |
|------|--------|---------|--------|-----------------|
| DB migration for World Model goals/causal-links/scenarios | P0-4 | ConcurrentHashMap | ecos_wm_goal, ecos_wm_causal_link, ecos_wm_scenario tables | 1 day |
| DB migration for Data Quality rules/issues | P0-5 | ConcurrentHashMap | ecos_dq_rule, ecos_dq_issue tables | 0.5 day |
| DB migration for Glossary terms | P1-5 | ConcurrentHashMap | ecos_glossary_term table | 0.5 day |
| DB migration for Marketplace assets | P1-5 | Static mock list | ecos_marketplace_asset table | 0.5 day |

### 🟡 Important: Functional Completeness

| Task | Module | Current State | Action | Effort |
|------|--------|--------------|--------|--------|
| Wire audit log to all CUD controllers | P0-6 | AuditLogDao exists but not wired | Add AOP or interceptor | 1 day |
| Wire Object relations from Ontology relationships | P0-1 | Returns empty array | Join query from ecos_ontology_relationship | 0.5 day |
| Wire Object timeline from audit log | P0-1 | Returns empty array | Query from ecos_audit_log | 0.5 day |
| Implement condition gateway expression editor | P0-3 | Not implemented | Simple expression input in WorkflowDesigner | 1 day |
| Implement real Agent test execution | P1-1 | Returns mock | Connect to HermesEngine | 0.5 day |
| Implement real Workflow test execution | P0-3 | Returns mock | Connect to state machine engine | 1 day |
| Wire NLQ Agent Runtime fallback | P1-6 | Fallback returns message | Connect to HermesEngine | 0.5 day |
| Action Designer backend CRUD | P0-2 | Not implemented | New Controller + Service + DB table | 1 day |
| Ontology version management | P0-2 | Not implemented | Version table + publish/rollback APIs | 1 day |

### 🟢 Nice-to-Have: Quality of Life

| Task | Module | Effort |
|------|--------|--------|
| Data permission interceptor on ObjectController | P0-1 | 0.5 day |
| Frontend route guard (redirect to /login) | P0-6 | 0.5 day |
| Ontology Domain management | P0-2 | 0.5 day |
| Dockerfile for sysman-boot | Infrastructure | 0.5 day |
| GitHub Actions CI (build + test) | DevOps | 1 day |

---

## 五、Implementation Status Summary Table

| # | Module | Design Doc | MVP Priority | Backend Real | DB Persistent | Frontend Real | Overall |
|---|--------|-----------|-------------|-------------|--------------|--------------|---------|
| 1 | IAM | 14/18-MVP | P0 | ✅ 90% | ✅ | ✅ | 🔵 90% |
| 2 | Ontology Studio | 07/1026 | P0 | ✅ 70% | ✅ | ✅ 80% | 🔵 75% |
| 3 | Object Runtime | 1027 | P0 | ✅ 85% | ✅ | ✅ 80% | 🔵 80% |
| 4 | Workflow Engine | 1029 | P0 | ✅ 70% | ✅ | ✅ 80% | 🔵 75% |
| 5 | Knowledge Center | 10/10213 | P0 | ⚠️ 40% | ⚠️ | ✅ 60% | 🟡 45% |
| 6 | Agent Runtime | 08/10211 | P0 | ✅ 85% | ✅ | ✅ 80% | 🔵 85% |
| 7 | Agent Mesh | 09/10212 | P1 | ✅ 85% | ✅ | ✅ 80% | 🔵 85% |
| 8 | World Model | 11/10214 | P0 | ⚠️ 40% | ❌ | ✅ 80% | 🟡 50% |
| 9 | Data Quality | 105 | P0 | ⚠️ 30% | ❌ | ✅ 80% | 🟡 45% |
| 10 | Unified Query | 10210 | P1 | ✅ 80% | ✅ | ⚠️ 50% | 🔵 70% |
| 11 | Data Catalog | 102 | P1 | ⚠️ 50% | ⚠️ | ✅ 70% | 🟡 55% |
| 12 | Pipeline Nodes | 103 | ✅ | ✅ | ✅ | ✅ | 🔵 100% |
| 13 | Marketplace | — | P2 | ⚠️ 30% | ❌ | ⚠️ 30% | 🟡 30% |
| 14 | Lakehouse | 104 | P2 | ❌ 0% | ❌ | ❌ 0% | 🔴 0% |
| 15 | MDM | 102 | P2 | ❌ 0% | ❌ | ❌ 0% | 🔴 0% |
| 16 | Agent Marketplace | 10211 | P2 | ❌ 0% | ❌ | ❌ 0% | 🔴 0% |
| 17 | Agent Reputation | — | P2 | ❌ 0% | ❌ | ❌ 0% | 🔴 0% |
| 18 | Autonomous Engine | 10216 | P2 | ❌ 0% | ❌ | ❌ 0% | 🔴 0% |
| 19 | Low-Code Builder | 10317 | P2 | ❌ 0% | ❌ | ❌ 0% | 🔴 0% |
| 20 | Unified Portal | 10320 | P2 | ❌ 0% | ❌ | ⚠️ 20% | 🔴 10% |

**Legend**: 🔴 <30% | 🟡 30-60% | 🔵 60-80% | ✅ >80%

---

## 六、Risk Register

| ID | Risk | Impact | Probability | Mitigation |
|----|------|--------|------------|------------|
| R-01 | Database migration conflicts between modules | Schema collision | LOW | Use `ecos_` prefix convention, separate DB files |
| R-02 | In-memory data loss on restart (World Model, DQ, Glossary) | User-facing data loss | HIGH | Sprint 0 priority to migrate to DB |
| R-03 | ObjectController hardcodes table names | Cannot add new entities dynamically | MEDIUM | Sprint 1: read ENTITY_TABLE from DB config |
| R-04 | No test coverage on core controllers | Regression risk | MEDIUM | Add integration tests in Sprint 0 |
| R-05 | Frontend mock data divergence from backend API shape | Mismatch in production | MEDIUM | API contract testing in Sprint 0 |
| R-06 | Architecture drift (self-built vs Temporal/Kafka/Neo4j) | Migration cost at V1 | MEDIUM | Document decision log, plan migration in v3 |
| R-07 | Single developer bottleneck (all modules in same monolith) | Delivery risk | HIGH | Keep monolith for MVP, prioritize task isolation |

---

## 七、Sprint 0 Execution Plan

### Sprint 0 Tasks (sorted by impact)

```
Week 1: DB Persistence Catch-up
├── DDL: ecos_wm_goal, ecos_wm_causal_link, ecos_wm_scenario
├── DDL: ecos_dq_rule, ecos_dq_issue
├── DDL: ecos_glossary_term, ecos_marketplace_asset
├── Refactor: Replace WorldModelController ConcurrentHashMap → JdbcTemplate
├── Refactor: Replace DqController ConcurrentHashMap → JdbcTemplate
├── Refactor: Replace GlossaryController ConcurrentHashMap → JdbcTemplate
└── Refactor: Replace MarketplaceController mock → JdbcTemplate

Week 2: Functional Completeness
├── Wire Object relations from Ontology relationships
├── Wire Object timeline from audit log
├── Wire audit AOP on all CUD endpoints
├── Implement Action Designer backend (Controller + Service + DB)
├── Implement condition expression editor (frontend)
├── Real Agent test execution (connect to HermesEngine)
├── Real Workflow test execution (connect to state machine)
├── NLQ Agent Runtime fallback integration
└── Frontend route guard

Week 3: Quality + Infrastructure
├── Data permission interceptor on ObjectController
├── Dockerfile for sysman-boot
├── GitHub Actions CI (compile + unit test)
├── API contract tests (backend ↔ frontend)
└── Sprint 1 detailed breakdown
```

---

## 八、Key Files for Sprint 0

### New DB Migrations
```
sysman-boot/src/main/resources/db/migration/V5__ecos_world_model.sql
sysman-boot/src/main/resources/db/migration/V6__ecos_data_quality.sql
sysman-boot/src/main/resources/db/migration/V7__ecos_glossary_marketplace.sql
```

### Refactored Controllers
```
sysman-impl/.../controller/WorldModelController.java     — Replace ConcurrentHashMap→JdbcTemplate
sysman-impl/.../controller/DqController.java             — Replace ConcurrentHashMap→JdbcTemplate
sysman-impl/.../controller/GlossaryController.java       — Replace ConcurrentHashMap→JdbcTemplate
sysman-impl/.../controller/MarketplaceController.java    — Replace mock→DB
```

### New Controllers
```
sysman-impl/.../controller/OntologyActionController.java — New: Action design + rule APIs
```

### Enhanced Controllers
```
sysman-impl/.../controller/ObjectController.java         — Wire relations + timeline
sysman-impl/.../controller/NLQController.java            — Wire Agent Runtime fallback
```

---

*End of Gap Analysis Report*
