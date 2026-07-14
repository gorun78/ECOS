# ECOS 产品完善方案 —— 从设计蓝图到可交付产品

> **编制**: 肖国荣  
> **日期**: 2026-06-22  
> **致**: ECOS-PMO（抄送 PM/ARCH/FE/BE/QA）  
> **前提**: 已阅读 ECOS_BACKEND_DESIGN_gemini.md 全卷 + 已完成代码审计  

---

## 〇、现实认知（PMO必须首先承认的三个事实）

在谈方案之前，先说清楚我们站在哪：

**事实一：设计文档和代码不是两张皮。**
sysman-api 已经有完整的 PDP/PEP/PIP/PAP 策略引擎、ABAC框架（`policy/engine/`）、行级/列级安全（`datapermission/` 含 `RowLevelSecurityService`/`ColumnLevelSecurityService`/`DataMaskingService`）、合规扫描（`compliance/scan/`）、审计日志（`audit/`）、KMS适配器（`kms/`）、多租户IAM（`iam/` 含9张表）。设计文档的第二章"高阶安全体系"不是空想——代码里已经有了80%的基础设施。

**事实二：但我们还没做到"产品"。**
buszhi 的工作流引擎可以跑，dccheng 的本体设计器 CRUD 全通，aimod 的 Agent 配置和管理可用。但 worldmodel 只有6个文件（3个Entity+Service+Repository），market 有骨架但没闭环，portal 只有2个文件。测试覆盖率1.55%，Git在6/18之后停摆。

**事实三：设计文档提出的一些技术选型是方向性的，不是当前必须。**
Doris+MinIO+Redis替代Spark是对的，但我们的客户当前跑在PostgreSQL上。gVisor/Firecracker沙盒是企业级要求，但MVP阶段用进程级隔离+资源限制就够了。Go/Rust 高性能引擎是远期目标，Java 17 + Spring Boot 3.2 完全能支撑前两个阶段。

**核心原则：不重写，只重构。不做新东西做到一半扔掉旧东西。**

---

## 一、方案总览：三阶段演进路线

```
Phase 1（6周）         Phase 2（8周）          Phase 3（12周）
"产品化"               "智能化"                 "平台化"
─────────────────────  ─────────────────────  ─────────────────────
安全面板可配置         因果推理引擎接入          Doris+MinIO数据湖
审计账本可查询         Agent Mesh真实编排       多语言Workbook沙盒
世界模型可编辑         知识图谱Neo4j集成         帕累托寻优引擎
数据市场可运营         OPA策略动态加载          企业数字孪生闭环
IAM+ABAC全链路打通     自学习案例库              多租户隔离
                       告警分级+WebSocket推送    OpenTelemetry全链路
```

**Phase 1 是硬交付。Phase 2/3 是路线图——PMO可以先评审Phase 1详细计划，Phase 2/3在Phase 1验收后细化。**

---

## 二、Phase 1：产品化（6周，到2026年8月3日）

> 目标：每个模块都能独立演示，不是"有代码"而是"能卖"。

### P1-1: 安全配置面板（第1-2周，BE+FE）

**现状**：sysman-api 有完整 ABAC/PDP-PEP 引擎，但缺少前端配置界面。Clearance Level、审计模式、沙盒开关目前硬编码。

**交付物**：

| ID | 任务 | 产出 | 负责人 |
|----|------|------|--------|
| P1-1.1 | SecurityConfigController | 用户安全配置CRUD端点（GET/PUT `/api/v1/security/profile`） | BE |
| P1-1.2 | 安全配置数据库表 | `user_security_configs` 表建表+迁移 | BE |
| P1-1.3 | 安全面板前端页面 | 对标设计文档"右上角高阶安全面板"——Clearance Level滑块、绑定工作站、审计模式切换、沙盒开关 | FE |
| P1-1.4 | ABAC策略管理页面 | OPA Rego策略的可视化编辑器+语法校验+版本管理 | FE+BE |

**验收**：
```bash
curl -X GET http://localhost:8081/sys-man/api/v1/security/profile -H "Authorization: Bearer $TOKEN"
# 应返回 clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory
```

**对应设计文档**：第二章 2.1-2.5

---

### P1-2: 密码学审计账本（第2-3周，BE+FE）

**现状**：有 `audit/` 模块（`AuditLog` entity + `IAuditLogService`），但没有 Merkle 链式签名。审计日志是普通数据库记录，可以被管理员修改。

**交付物**：

| ID | 任务 | 产出 | 负责人 |
|----|------|------|--------|
| P1-2.1 | CryptographicAuditLedger 表 | 建表（含 pre_hash/current_hash/operator_signature） | BE |
| P1-2.2 | AuditLedgerService | 双写逻辑：写业务表→同时写审计账本，SHA256链式哈希 | BE |
| P1-2.3 | AuditLogInterceptor | AOP拦截器，自动拦截所有 `@Auditable` 注解的方法 | BE |
| P1-2.4 | 审计日志查询页面 | 按操作员/时间/动作类型筛选，展示哈希链验证状态 | FE |

**验收**：
```bash
# 触发一次写操作后查询审计链
curl http://localhost:8081/sys-man/api/v1/audit/ledger?operator=admin&page=1
# 应返回链式哈希记录，pre_hash 指向上一块
```

**对应设计文档**：第二章 2.3 + 第十三章 13.2

---

### P1-3: 世界模型产品化（第2-4周，BE+FE）

**现状**：worldmodel 模块只有6个文件，3个Entity（GoalEntity/CausalLinkEntity/ScenarioEntity）+ WorldModelRepository + WorldModelService。没有Controller，前端无页面。

**交付物**：

| ID | 任务 | 产出 | 负责人 |
|----|------|------|--------|
| P1-3.1 | WorldModelController | 场景CRUD（8个端点）+ 目标CRUD + 因果链CRUD | BE |
| P1-3.2 | WorldModel 建表 | goal/scenario/causal_link 三张表 | BE |
| P1-3.3 | WorldModelService 实现 | 非stub实现：JdbcTemplate真实查询 | BE |
| P1-3.4 | 前端场景编辑器 | 可视化场景创建+目标绑定+因果链拖拽连线 | FE |
| P1-3.5 | 前端世界模型总览 | 场景列表+状态一览（对标设计文档第九章状态机） | FE |

**验收**：
```bash
# 场景CRUD全链路
curl -X POST http://localhost:8081/sys-man/api/v1/ecos/scenarios -d '{"name":"产线故障演练","description":"模拟Q3产线停机场景"}'
curl http://localhost:8081/sys-man/api/v1/ecos/scenarios
# 因果链创建
curl -X POST http://localhost:8081/sys-man/api/v1/ecos/causal-links -d '{"scenarioId":"...","sourceGoalId":"...","targetGoalId":"...","effectType":"INCREASES"}'
```

**对应设计文档**：第九章

---

### P1-4: 数据市场产品化（第3-4周，BE+FE）

**现状**：market 模块已有 MarketplaceAssetEntity/MarketplaceAccessRequestEntity/MarketplaceReviewEntity + MarketplaceRepository + MarketplaceService + MarketplaceController。需要补全业务闭环。

**交付物**：

| ID | 任务 | 产出 | 负责人 |
|----|------|------|--------|
| P1-4.1 | Marketplace 端点补全 | 资产上架/下架/搜索/分类过滤 + 申请提交/审批/回调 | BE |
| P1-4.2 | 数据市场前端页面 | 资产目录+详情+申请流程+审批管理 | FE |
| P1-4.3 | 数据资产与Ontology关联 | 市场资产关联到dccheng模块的本体实体 | BE |

**验收**：
```bash
# 资产上架→申请→审批全链路
curl -X POST http://localhost:8081/sys-man/api/v1/marketplace/assets -d '{"name":"客户360视图","type":"DATASET","ontologyEntityId":"ent123"}'
curl -X POST http://localhost:8081/sys-man/api/v1/marketplace/access-requests -d '{"assetId":"...","reason":"业务分析"}'
curl -X PUT http://localhost:8081/sys-man/api/v1/marketplace/access-requests/.../approve
```

**对应设计文档**：第七章 7.1（元数据目录的市场化呈现）

---

### P1-5: IAM + ABAC 全链路打通（第4-5周，BE+FE）

**现状**：IAM有完整的User/Role/Permission/Org/Tenant体系，ABAC有PDP/PEP引擎。但两者没有打通——PEP拦截器没有接入实际用户认证流程。

**交付物**：

| ID | 任务 | 产出 | 负责人 |
|----|------|------|--------|
| P1-5.1 | SecurityInterceptor 集成 | Spring Interceptor 在请求链路中注入ABAC上下文 | BE |
| P1-5.2 | 动态字段脱敏演示 | Clearance Level < 3 的用户查询敏感实体时自动哈希字段 | BE |
| P1-5.3 | 前端IAM管理页面完善 | 用户-角色-权限-组织树 完整CRUD | FE |
| P1-5.4 | 登录页面对接真实IAM | 当前登录绕过IAM，改为JWT+Session双重验证 | BE+FE |

**验收**：
```bash
# 低权限用户查询应看到脱敏数据
curl http://localhost:8081/sys-man/api/v1/ecos/objects/Customer/CUST001 \
  -H "Authorization: Bearer $LOW_CLEARANCE_TOKEN"
# 返回的 phone/email 字段应为哈希值或掩码
```

**对应设计文档**：第二章 2.2 + 第十三章 13.1

---

### P1-6: 门户聚合与统一搜索（第5-6周，BE+FE）

**现状**：portal 模块只有2个文件（MenuController + FrontendBridgeController）。

**交付物**：

| ID | 任务 | 产出 | 负责人 |
|----|------|------|--------|
| P1-6.1 | UnifiedSearchController | 跨模块统一搜索API（DataCatalog+Ontology+Market+Workflow） | BE |
| P1-6.2 | 门户首页 | 数据资产总览+待办任务+系统健康KPI+最近访问 | FE |
| P1-6.3 | 全文搜索引擎接入 | Elasticsearch 索引同步（替代当前 LIKE 查询） | BE |

**验收**：
```bash
curl "http://localhost:8081/sys-man/api/v1/portal/search?q=客户"
# 应返回 数据目录+本体实体+市场资产+工作流 的混合搜索结果
```

**对应设计文档**：第七章 7.1（高性能倒排搜索体系）

---

### P1-7: 测试补齐 + 持续集成（并行，全6周）

**交付物**：

| ID | 任务 | 产出 | 负责人 |
|----|------|------|--------|
| P1-7.1 | 核心Controller集成测试 | 每个P1新增Controller至少1个 `@SpringBootTest` | BE+QA |
| P1-7.2 | Playwright E2E 打通 | Chromium安装→核心流程自动化测试（安全配置/世界模型/数据市场） | QA |
| P1-7.3 | CI流水线 | GitHub Actions：push→compile→test→playwright | QA |

**目标**：测试文件从14个增长到≥35个。Playwright全绿。

---

## 三、Phase 1 验收标准（不可妥协）

6周后，以下5个场景必须能用浏览器完整走通：

1. **安全配置**：管理员打开安全面板→设置Clearance=3→保存→低权限用户查询数据被脱敏
2. **审计追踪**：执行一次Ontology实体修改→打开审计页面→看到链式哈希记录→验证哈希链完整性
3. **世界模型**：创建场景"产线故障"→添加目标"恢复生产"→添加因果链"备件不足→停机延长"→保存并查看拓扑图
4. **数据市场**：数据Owner上架"客户360"→分析师申请访问→Owner审批通过→分析师可查询
5. **统一搜索**：在门户搜索框输入"客户"→返回数据目录、本体实体、市场资产、相关文档

---

## 四、Phase 2/3 概要（PMO了解方向即可，Phase 1验收后再细化）

### Phase 2：智能化（8周）
- **P2-1**: Neo4j知识图谱集成 → 替换dccheng的Ontology关系查询（当前是stream过滤，设计文档要求Cypher）
- **P2-2**: Agent Mesh真实编排 → Coordinator→Researcher→Analyst 链路，ReAct循环
- **P2-3**: OPA Rego策略动态加载 → 当前ABAC策略硬编码，改为OPA引擎热加载
- **P2-4**: 因果推理引擎 → 结构因果模型(SCM) + Do-calculus + 贝叶斯根因反推
- **P2-5**: 自学习案例库 → 决策记忆沉淀+对比学习+案例检索召回
- **P2-6**: 分级告警+WebSocket推送 → 告警规则引擎+实时推送+强制确认通道

### Phase 3：平台化（12周）
- **P3-1**: Doris+MinIO数据湖 → 替代纯PostgreSQL分析查询
- **P3-2**: 多语言Workbook沙盒 → Python/R/SQL交互式计算+容器隔离
- **P3-3**: 帕累托寻优引擎 → NSGA-II多目标优化+帕累托前沿可视化
- **P3-4**: 企业数字孪生闭环 → 物理设备MQTT/OPC-UA接入+实时状态机+双向对齐
- **P3-5**: OpenTelemetry全链路 → 分布式追踪+Token消耗审计
- **P3-6**: 多租户隔离+计费 → Tenant资源配额+用量统计

---

## 五、对PMO的硬性要求

### 5.1 每日站会格式改为"代码检查会"（延续上次指令）

```
[模块] [提交者] [commit hash] [变更摘要] [测试结果]
```

不讨论设计、不讨论规划、不讨论"应该怎么做"。

### 5.2 看板规则

- 任务卡片DONE = commit hash + curl验收通过截图 + 测试通过截图
- 三缺一不可标记DONE
- 不允许"后端DONE/前端TODO"拆分——前后端一起交付才算DONE

### 5.3 质量控制

- 新增代码必须有测试（Controller → `@SpringBootTest`，Service → `@MockitoTest`）
- 前端页面必须有至少一个Playwright冒烟用例
- 不允许用mock数据填充前端显示——后端端点必须先通

### 5.4 Phase 1 每两周的里程碑检查

| 时间 | 里程碑 | 检查方式 |
|------|--------|----------|
| 第2周末 | P1-1完成 + P1-2完成 | 安全面板可配置 + 审计链可查询 |
| 第4周末 | P1-3完成 + P1-4完成 | 世界模型可编辑 + 数据市场可运营 |
| 第6周末 | P1-5/6/7完成 | 全部5个验收场景走通 + 测试≥35文件 |

---

**最后一句话：Phase 1不做任何新基础设施引入。Java 17 + Spring Boot 3.2 + PostgreSQL + React 19。Phase 2再讨论Neo4j/OPA/Elasticsearch，Phase 3再讨论Doris/MinIO/gVisor。先把手里的牌打好，再想换牌。**
