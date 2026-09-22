# ECOS 项目实现逻辑文档

> 来源: PMO 历史任务汇总 | 日期: 2026-09-22 | 责任人: AI Agent
> 版本: v1.0
> 追溯: ARCHITECTURE-RULES v1.1 / 架构铁律 v1.3 / 数据湖存储分层规范 v1.2 / 数据库访问规范 v1.0 / 技术债修复计划 / 六引擎 AGENTS.md + docs/ 全量扫描

---

## 〇、文档目的与覆盖范围

本文档汇总 ECOS 工程历史任务执行过程中产生的**定义、决策和判断**，以六引擎为分类角度，**最近优先**排列。每项决策标注来源文件，供后续开发参考。

**覆盖范围**：
- `docs/00-架构/ARCHITECTURE-RULES.md`（v1.1）
- `.trae/rules/架构铁律.md`（v1.3）
- `.trae/rules/数据湖存储分层规范.md`（v1.2）
- `.trae/rules/数据库访问规范.md`（v1.0）
- `docs/30-cross-cutting-docs/pmo/ECOS-技术债修复计划.md`
- `docs/21-runtime/legacy-plans/current-plan.md`（v1.4）
- 六引擎各自 `AGENTS.md` + `docs/engine-*/` 全部文档
- `ecos_backend/` 根级 + runtime 各子模块 `AGENTS.md`

---

## 一、六引擎总纲（架构宪法层决策）

> 来源: `ARCHITECTURE-RULES.md` §0.2/§0.3 + `架构铁律.md` v1.3

### 1.1 五行 + 横切层定位

| 引擎 | 五行/角色 | 端口 | 职责 | 关键禁止项 |
|:--|:--|:--:|:--|:--|
| **security-engine** | 护（横切，不入五行） | 18081 | 认证/授权/审计/脱敏/ABAC | 不执行业务规则判定 |
| **data-engine** | 土（D 数据） | 18082 | 数据源/管道/血缘/DQ/查询 | 管道不执行 >30min 任务；血缘不追踪 Neo4j |
| **ontology-engine** | 金（I 信息·本体） | 18083 | 本体建模/实体/关系/版本 | 不直接写 Neo4j |
| **kb-engine** | 水（K 知识） | 18086 | KG 存储/检索/RAG/规则 CRUD | 不执行规则判定；不直接调 LLM |
| **cognitive-engine** | 木（C 认知） | 18084（v2 后 run in aiming） | 因果推理/情景推演/混合推理 | 推理结果不落盘；不引入规则引擎 |
| **ai-engine** | 火（W 智慧） | 18084 | Agent/Loop/Memory/LLM 调用 | — |

**命名铁律**：`ge/zhi/cheng/ming` 是**服务层转化**名称，不得用于引擎层对象管理模块。

### 1.2 服务层四转化（格致诚明）

| 转化名 | 物理含义 | 物理落点 |
|:--|:--|:--|
| ge（格物） | D→I 数据→信息 | 代码寄居 `data-engine-impl/transform` 包（:18082），无独立 service |
| zhi（致知） | I→K 信息→知识 | `ontology-engine` → `kb-engine` `KgSyncService.sync`（REST :18086） |
| cheng（诚） | K→C 知识→认知 | 待独立 service（阶段 D4） |
| ming（明） | K→W 知识→智慧 | 待独立 service（阶段 D4） |

### 1.3 依赖方向铁律

- 下层禁止 import 上层
- 跨引擎只调 API（铁律 2.1），**禁 import 对方 impl**
- 跨模块走 `PipelineEvent`（common-api）或 REST
- 基础设施访问（PG/Neo4j/MinIO/Doris/Git）统一收敛 `runtime-access`，**禁各自 new Driver**（铁律 2.5）

### 1.4 三档分级

```
standard    → PG only（中小企业）
enterprise  → PG + Neo4j（因果链 >3 层启用图谱）
ultimate    → PG + Neo4j + Doris（单表 >100 万行启用列存）
```

**约束**：不加新 Maven 模块（基线 13），不加新 Docker 容器。

### 1.5 微服务 v2 架构（PMO-49，2026-09-11）

| 部署单元 | 端口 | 封装引擎 |
|:--|:--:|:--|
| `gateway` | 8080 | 纯组织/认证 facade（7 独立 JAR 入口） |
| `services/sysman` | 18081 | security-engine-impl |
| `services/datanet` | 18082 | data-engine-impl |
| `services/buszhi` | 18083 | ontology-engine-impl + 工作流 |
| `services/dccheng` | 18086 | kb-engine-impl（PMO-60 后 cognitive 迁出） |
| `services/aiming` | 18084 | ai-engine-impl + cognitive-engine-impl + llm-gateway |
| `workspace` | 18090 | 场景应用层（仅依赖 API 契约） |

**ADR-3 数据隔离**：standard = 共享 PG + Schema 隔离；enterprise/ultimate = 每 service 独立 PG database。
**ADR-7 服务间信任**：网关统一认证 + 双通道凭证 + 网络隔离；gateway 校验后 **strip 外部 `X-ECOS-*` 头并重新注入**（防伪造）。
**ADR-8 审计统一 Kafka**：写操作走 Kafka `ecos.audit`（`KafkaTopics.AUDIT` 常量），sysman 消费落库；原 REST `POST /api/v1/security/audit/log` 保留过渡期（Phase 6 后下线）。

### 1.6 关键架构判断

1. **dccheng 拆解不修**（决策 D1）：对象代码归还引擎层，`cheng` 名归还服务层 K→C 转化
2. **agent/chat 权威 = ai-engine**（决策 D2）
3. **Redis 分版本**（决策 D3）：standard 不引 Redis（PG-only + Caffeine）；enterprise/ultimate 加 Redis TTL
4. **基础设施访问含 Git**（决策 D5）：PG/Neo4j/MinIO/Doris/Git 五类收敛 runtime-access
5. **cognitive 从 dccheng 迁入 aiming**（PMO-60 P0，2026-09-14）
6. **§1.6 任务统一接入入口**（2026-09-22 新增）：即时任务走 `ITaskManagementService.submitTask + executeTask`；定时任务走 `td_runtime_task_plan` + `td_runtime_task`；业务级监控走「异步任务中心 Agent」

### 1.7 技术债修复主线（2026-08-21 肖国荣）

| 阶段 | 核心内容 |
|:--|:--|
| A（P0 止血） | dccheng 拆解 / agent-chat 统一 ai-engine / knowledge-rag 统一 kb-engine / security-mask 归 security-engine / 前端去重 |
| A+（runtime 拆解） | 安全迁 security-engine / 数据工程迁 data-engine / agent.mesh 迁 ai-engine / **新建 runtime-access** 收敛五类 Driver / 删死代码 |
| B（P1 内存） | Token blacklist → Caffeine + Redis TTL / InMemory* → Caffeine / Dict/Config 缓存预热 |
| C（P1-P2 拆分） | WorkshopView 2243 行 / CausalReasonerServiceImpl 726 行 / buildWhereClause 策略模式 |
| D（P3 守护） | ArchUnit 扩展 / services 收敛为 ge/zhi/cheng/ming |

---

## 二、runtime 横切底座（器）

> 来源: `架构铁律.md` §2.5 + 各 runtime 子模块 AGENTS.md

### 2.1 六子模块定位

| 模块 | 职责 | 关键约束 |
|:--|:--|:--|
| **common-api** | 全仓共享契约（PipelineEvent/KafkaTopics/IEngine/ApiResponse/异常层级） | 0 业务依赖（enforcer 守门） |
| **runtime-access** | Driver/Client 收敛（PG/Neo4j/MinIO/Git/DuckDB） | 只注入不 new；铁律 2.5 #1 |
| **runtime-task** | 统一调度，取代各引擎 ScheduledExecutorService | 铁律 2.5 #3 |
| **runtime-monitor** | 综合监控/策略判定/插件告警 | 铁律 2.5 #4；**禁止反向依赖业务模块** |
| **llm-gateway** | LLM 调用唯一出口，provider/计量/会话 | `DEEPSEEK_API_KEY` 仅 gateway 持有 |
| **runtime-event** | Kafka 事件总线 + 内存 fallback | 可降级；DLQ = `<topic>.DLT` |

### 2.2 关键决策

- **D6**：llm-gateway 归属不变（留 runtime）
- **D7**：命名 `runtime-access`（非 core/common），放 runtime 下与 runtime-task 并列
- **P8-A/B/C 模块结构调整**（2026-09-11）：`common/common-api` → `runtime/common-api`；保 artifact 稳定，仅动物理目录（Java 包名不变）
- **runtime 不可能反向依赖业务模块**（runtime-monitor 明确禁止 import engine/services/workspace）

---

## 三、security-engine（护）

> 来源: `security-engine/AGENTS.md` + `docs/engine-security/1-sysman-legacy/` 12 文件

### 3.1 定位与边界

- **横切保护层**，纵切各层公共底座，**不入五行、不做转化**
- 职责：认证（JWT 签发/验证/刷新）、授权（RBAC/ABAC/PBAC）、审计日志、数据脱敏（SHA256/PHONE/EMAIL/ID_CARD/AMOUNT）、数据权限（行级 RLS / 列级 CLS）、加密服务（AES-256-GCM + KMS）
- **底层引擎**，不依赖任何外部端点

### 3.2 安全接入铁律（8 条，🔴 强制）

| # | 规则 |
|:--|:--|
| 1 | data-engine 查询前调 `POST /api/security/rls/apply` 注入 RLS WHERE |
| 2 | 查询结果返回前调 `POST /api/security/cls/columns` 过滤敏感列 |
| 3 | 敏感字段返回前调 `POST /api/security/mask` 脱敏 |
| 4 | Agent 工具调用/Function 执行前调 `POST /api/v1/security/policy-engine/evaluate`（OPA ABAC） |
| 5 | 所有写操作发 **Kafka `ecos.audit`**（新代码一律走 Kafka，REST 端点过渡期） |
| 6 | **security-engine 不可用时默认拒绝**（宁可误拒不可误放） |
| 7 | 各引擎禁止在自身代码实现权限/脱敏/审计逻辑 |
| 8 | **安全集成强制卡**：Task 涉及敏感数据必须显式列出 security-engine 集成项，grep 命中 0 直接判 FAIL（可升级 P0） |

### 3.3 三滤波器（新增 Controller 必查）

实证：**路径重写先于鉴权**（`VersionPrefixRewriteFilter` `@Order(MIN+10)` 早于 Spring Security `-100`）。

三步判据（替代双路径机械表述）：
1. 该端点是否应匿名？否 → permitAll 一律不写
2. 前缀是否在 `V1_REWRITE_MAP` 内？是 → 只写裸路径
3. 强制匿名回归（无 token 403 / 带 token 200）

### 3.4 阶段决策记录

**Phase 1（PMO-01~06）**：
- 内置 5 角色（admin/data-manager/ontology-designer/knowledge-engineer/analyst）——不设 viewer（只读即等同）、不设 operator（admin 覆盖）
- 登录安全：5 次失败 → 锁定 15 分钟（**必须消费 SysConfig，不硬编码**）
- 错误码：ACCOUNT_LOCKED(423) / PASSWORD_CHANGE_REQUIRED(428) / PASSWORD_EXPIRED(429) / PASSWORD_WEAK(422)
- 安全中心三 Tab 架构（事前/事中/事后）
- 3 个 `SecurityCenter.tsx` 副本 → 删除 2 个统一 1 个再拆分

**Phase 2（PMO-07）**：
- RLS 表 `ecos_rls_policy`（同表多策略用 **AND 连接**，按 priority 排序）
- CLS 表 `ecos_cls_policy`（优先级：用户级 > 角色级 > 全局）
- ABAC 路径对齐：新增 `SecurityPolicyController` 转发到 OPA，**不改旧端点**（兼容）
- Token 黑名单：`ecos_token_blacklist`（jti 唯一，TTL = token 剩余有效期）
- **RLS/CLS 策略表不设物理外键**（PG 性能考量）
- 脱敏联动：`PipelineEvent("DATA_MASKING_RULES_CHANGED")` 异步缓存刷新

### 3.5 关键判断

| 判断 | 原因 |
|:--|:--|
| 默认 DENY 而非 FALLBACK ALLOW | 2026-09-16 事故：误补 permitAll 致未认证可读数据源 host/port/username/jdbcUrl |
| 审计走 Kafka 而非 REST | 不阻塞主流程、解耦、集中消费落库 |
| ABAC 走新 Controller 转发而非改旧端点 | 兼容现有调用方 |
| 锁定逻辑必须消费 SysConfig | 可配性 + 多租户差异化预留 |
| "事中 Tab" Phase 1 只做占位 | 降低 Phase 1 复杂度，留 Phase 2 |

---

## 四、data-engine（土·D）

> 来源: `data-engine/AGENTS.md` + `docs/engine-data/3-data-legacy/` 11 文件

### 4.1 定位与边界

- **底层引擎**，无外部依赖，数据唯一事实源
- 职责六项：数据源管理 / 数据管道 / 数据目录 / 数据血缘 / 数据质量 / 管道任务调度
- 禁止：不操作其他引擎表；管道不执行 >30min 任务；血缘不追踪 Neo4j

### 4.2 数据湖五层模型（权威口径）

| 枚举 | 物理载体 | 生产者 | 消费者 |
|:--|:--|:--|:--|
| `SOURCE` | 外部 PG/MySQL/Oracle/ClickHouse | — | 采集型管道 |
| `RAW` | **MinIO 数据湖** | 采集型管道（`SINK_MINIO`） | 数据管道、数据质量 |
| `CURATED`（DW 层） | DW 库表（PG/Doris） | 数据管道、数据质量 | 本体、知识、管道 |
| `SEMANTIC` | 本体引擎存储 | 本体引擎（ge D→I） | 知识引擎（zhi I→K） |
| `APPLICATION` | DW 库表 | 聚合管道 | 场景工作台 |

**铁律**：`DataLayer` 枚举为 Java 侧唯一权威定义（`data-engine-api`），其他工作台**不得新增层名**（ODS/DWD/DWS 一律不使用）。

**MinIO key 规范**：
- 结构化：`raw/structured/{source}/{table}/dt=YYYY-MM-DD/{table}_{yyyyMMddHHmmss}.csv`
- 非结构化：`raw/unstructured/{source}/{docId}/{originalFileName}`
- 旧前缀 `datalake/` 保留可读，不再新写

### 4.3 读写边界（强制）

| 工作台 | 近源层（RAW） | DW 层（CURATED） | 语义层（SEMANTIC） |
|:--|:--|:--|:--|
| 数据工作台（data-engine） | **读写** | **读写** | — |
| 本体工作台（ontology-engine） | **禁止直读** | **只读** | **读写** |
| 知识工作台（kb-engine） | **禁止直读** | **只读** | 只读 |

**铁律**：本体与知识**从 DW 层取数**，不得绕过 DW 层直读 MinIO 近源层或外部源系统。

### 4.4 DQ 数据质量模型

- **6 维度加权**：COMPLETENESS=30 / ACCURACY=25 / CONSISTENCY=15 / UNIQUENESS=15 / VALIDITY=10 / FRESHNESS=5
- 等级阈值：A≥0.95 / B≥0.85 / C≥0.70 / D≥0.50 / F<0.50
- **评估器不写库**（只算+返回，落库在 DqScoreService）
- 告警分级：P0（5min）/ P1（15min）/ P2（1h）/ P3（24h）；P2 5min 未 ack → P1，P1 15min 未 ack → P0
- **告警必须接 runtime-core，禁止在 data-engine 自建监控**
- 双轨规则系统完全合并到 `ecos_dq.dq_rule`（旧端点保留只读兼容 2 迭代）

### 4.5 数据源元数据获取策略

- 策略枚举：`ON_SAVE` / `ON_SCHEDULE` / `MANUAL` / `ON_DEMAND`（不推荐）
- 行计数三模式：`EXACT`（SELECT COUNT(*)）/ `ESTIMATE`（PG 用 `pg_stat_user_tables`）/ `OFF`
- **元数据缓存分页**（DB 不做分页）：单数据源下属表 <100，Caffeine 缓存 5min TTL

### 4.6 关键判断

| 判断 | 原因 |
|:--|:--|
| 血缘从正则改为 JSqlParser | 支持 CTE/JOIN/子查询，字段级血缘 |
| 管道不执行 >30min 任务 | 防止长时间占用连接 |
| DQ 评分 Phase 2 只读 `dq_rule_check`，不拉样本 | 性能/复杂度取舍 |
| 非结构化解析能力上移 runtime-access | data-engine 与 kb-engine 共用，消除重复实现 |

---

## 五、ontology-engine（金·I）

> 来源: `ontology-engine/AGENTS.md` + `docs/engine-ontology/4-onto-legacy/` 8 文件 + `service/ge/AGENTS.md`

### 5.1 定位与边界

- 本体模型 / 实体 / 关系 / 版本管理 / 工作流 / 领域管理 / 本体→KG 同步
- 端口 18083，依赖 PostgreSQL + kb-engine-api（KG 同步）
- **不直接写 Neo4j**，必须经 kb-engine 的 `POST :18086/api/v1/kb/graph/sync`
- **对象实例不支持物理删除**，只标记逻辑删除（R9 只加不删）

### 5.2 核心定位判断

> "本体工作台不是画 ER 图的工具，是让企业数据可理解、可推理、可治理的语义基础设施。"
> "自动发现是入口，Function 是引擎，提案是治理，ER 图是锦上添花。"

- **自动发现提升为 Phase 4 核心用户入口**（数据驱动建模优先于手动画图）
- 工作流：连接数据源 → 自动发现候选实体 → 预览+勾选 → 一键生成本体 → 手动编辑

### 5.3 Function 沙箱引擎

- **不是通用脚本引擎，是受限 SQL 聚合表达式计算器**
- 允许：`SUM/AVG/COUNT/MIN/MAX`、`+ - * / %`、`CASE WHEN`、`COALESCE/NULLIF`、`ABS/ROUND/CEIL/FLOOR`、`WHERE`
- 禁止：`Runtime.exec`/反射/网络 IO/文件 IO、变量声明、循环、子查询、跨表 JOIN
- **Function 不跨表 JOIN——单表计算，跨表走 cognitive-engine**（I 层与 C 层职责切分）
- 执行模型：表达式编译 → JdbcTemplate 参数化查询 → 单值返回，超时 5s
- 缓存：Caffeine TTL 300s，本体变更时主动失效
- **无审计日志的 Function 执行拒收**

### 5.4 提案系统（治理唯一变更入口）

- 每次变更：提案 → 审批 → 执行 → 版本发布
- 审批通过 → **自动创建 Draft → 执行变更 → 版本发布**（不打通的方案拒收）
- 乐观锁：`optimistic_lock_version` 冲突返回 ONT-409
- 回滚支持：保留变更前 JSON 快照

### 5.5 ge（格物 D→I）转化层

- **代码寄居 `data-engine-impl/transform` 包**，无独立 service（铁律 §5.1#10 不新增 Maven 模块）
- 6 步 Transform 链：`Cleansing → Mapping → TypeConversion → Validation → Aggregation → Calculator`
- **ge 不落自有表**（transform 为无状态内存计算）
- 交叉引擎事件：`PipelineEvent`（COLLECTION_COMPLETED/TRANSFORM_COMPLETED）

### 5.6 Wave 3.1 收口关键决策

- 多租户 RLS：`tenant_id VARCHAR(32)` + 二级索引，`OntologyDomainRepository` 全部 CRUD 改 RLS-aware
- Neo4j 同步：`@Autowired(required=false) Driver` + `@Profile({"enterprise","flagship"})`，NPE 防护（driver null → disabled + reason 返回）
- **0 自建 Driver**（grep 验证通过）

---

## 六、kb-engine（水·K）

> 来源: `kb-engine/` 4 层 AGENTS.md + `docs/engine-kb/README.md`

### 6.1 定位与边界

- 知识库引擎，端口 18086，依赖 Neo4j（enterprise 档）
- 职责 7 个核心服务：KnowledgeGraphService / KnowledgeRetrievalService / RuleGraphService / KGWriterService / ExtractionSourceLoader / ExpertRuleService / ComplianceRuleMapper
- **底层引擎**，不依赖其他引擎
- **禁止**：不执行规则判定（cognitive 的事）；不直接调 LLM（ai-engine 的事）；Neo4j 仅 enterprise/ultimate 启用

### 6.2 致知（I→K）流程

#### 6.2.1 触发入口（三入口）

| 入口 | 端点 | 用途 |
|:--|:--|:--|
| 全量/分层同步 | `POST /api/v1/knowledge/sync/trigger` | 全量重建（`FULL_SYNC`） |
| 模式化构建 | `POST /api/v1/knowledge/graph/build` | FULL/INCREMENTAL + dry-run |
| 结构化抽取（K1 主力） | `POST /api/v1/knowledge/extract/structured` | 默认 INCREMENTAL，走 `ITaskManagementService` |

#### 6.2.2 核心执行流程（`KgSyncServiceImpl.triggerBuildSync`）

1. **写 RUNNING 台账** → `kg_sync_log`（op = FULL_SYNC / INCREMENTAL_SYNC / DRY_RUN）
2. **发 Kafka `ecos.audit`**（Kafka 不可用时 log 兜底）
3. **@Async 异步执行** `runKgMapper("ALL", jobId, mode, dryRun)`（Spring taskExecutor，避免长任务阻塞 HTTP）
4. **Step 1 — 骨架/版本对齐（B1）**：
   - 读 `kb_ontology_snapshot`（每本体最新生效快照，含 `entity_codes[]` JSONB）
   - 调 `kgMapper.syncFromOntology(objectType, jobId)` 把快照版本打到既有 `graph_node.ontology_version`
   - **Q4 裁决**：不物化本体实体为图节点，类型仅作 `graph_node.node_type` 属性
5. **Step 2 — 契约驱动实例抽取（B3-2）**：
   - 调 `KbEntityInstanceExtractionService.extract(ontologyId, jobId, incremental, dryRun)`

#### 6.2.3 实例抽取核心引擎（`KbEntityInstanceExtractionService.extract`）

**五步主流程**：

| 步骤 | 动作 | 数据源 |
|:--|:--|:--|
| ① 读本体快照 | `kb_ontology_snapshot`（`DISTINCT ON ontology_id ORDER BY created_at DESC`） | PG 本库 |
| ② 拉 DW 资源索引 | `GET datanet:/api/v1/engine/data/layers/CURATED` → byId/byName 双索引 | data-engine REST |
| ③ 拉映射契约 | `GET ontology:/ontology/entity-mappings?ontologyId=` | ontology-engine REST |
| ④ 拉实体/关系定义 | `GET ontology:/ecos/ontologies/{id}/entities` + `relationships` | ontology-engine REST |
| ⑤ 逐映射抽取 | `extractForMapping()` → 分页读 DW 行 → 幂等 upsert `graph_node`/`graph_edge` | DW 层 REST |

**FULL vs INCREMENTAL 语义**：

| 维度 | FULL | INCREMENTAL |
|:--|:--|:--|
| 水位线 | 忽略，重读全部 DW 实例行 | 按 `kb_extract_watermark` 三元组续读 |
| 适用场景 | 本体发布后首次构建/修复/重跑 | 周期增量（K4 触发源） |
| op 标识 | `FULL_SYNC` | `INCREMENTAL_SYNC` |
| 水位落库 | 不更新 watermark | 每行读完后 upsert `nextWatermark` |
| 模式归一化 | 非 INCREMENTAL 一律归一为 FULL（保守安全） | — |

#### 6.2.4 C1~C4 校验规则

| 校验 | 规则 | 失败处置 | 代码位置 |
|:--|:--|:--|:--|
| **C1** | 类型存在性：`entityCode` ∈ 快照 `entity_codes[]` 集合 | 拒绝构建，issue `UNKNOWN_ENTITY_TYPE` | `extractForMapping` |
| **C2** | 关系合法性：`(sourceType, relation, targetType)` ∈ 本体关系定义 | 跳过该边 + 告警，issue `C2_SKIP` | `collectEdges` + `flushEdges` |
| **C3** | 属性完整性：节点属性 ⊆ 本体属性集合（必填缺失告警，不阻断） | ⚠️ 当前未显式实现，预留 | 待后续批次 |
| **C4** | 映射有效性：DW 资源存在 → 列定义存在 → 字段映射命中物理列（三道闸） | 拒绝该实体实例化，issue `INVALID_MAPPING`，`invalidMappings++` | `extractForMapping` 三段 |

**C4 三道闸详解**：
1. `resourceIndex.resolve(datasetId, resourceName)` → null → `INVALID_MAPPING`（资源不在 CURATED 层）
2. `loadMetadataFields(resourceId)` → 空 → `INVALID_MAPPING`（无字段元数据，未采集）
3. `resolveFieldRefs(fieldMappings, columnNames)` → 空 → `INVALID_MAPPING`（字段无一命中 DW 物理列）

#### 6.2.5 水位线机制（`kb_extract_watermark`）

- **粒度**：`ontologyId + entityCode + resourceId` 三元组（每 (本体, 实体, DW资源) 独立水位）
- **读**：仅 INCREMENTAL 模式，表缺失/异常 → null（按全量处理，容错降级不阻断）
- **写**：仅非 dry-run 且 lastWatermark 非空 → `INSERT ... ON CONFLICT DO UPDATE`（幂等 upsert）
- **DW 行读取**：`GET /api/v1/engine/data/layers/CURATED/resources/{id}/rows?watermark=&limit=500` 返 `{rows[], nextWatermark, hasMore}`

#### 6.2.6 幂等 upsert 与溯源

- **节点 id**：`sha256(ontologyId::nodeType::pkValue)` 前 32 hex
- **边 id**：`"kgrel:" + sha256(ontologyId:sourceType:relation:sourcePk:fkValue)` 前 32 hex
- **upsert 语义**：`ON CONFLICT (id) DO UPDATE` + `RETURNING (xmax = 0)` 区分 create(true)/update(false)
- **边统一 flush**：全部节点 upsert 完后做端点存在性批量查询（`graph_node.id IN (...)` 分片 500），端点缺失 → skip（不强行插入悬空边）
- **溯源 4 列**：`ontology_id` / `ontology_version` / `source_resource_id` / `source_pk`
- **速率防御**：`PAGE_LIMIT=500` × `MAX_PAGES=200` = 10 万行上限，命中记 `ROWS_TRUNCATED`

#### 6.2.7 非结构化摄入（A3 过渡态）

**7 步完整链路**（`KnowledgeDocIngestService.ingest`）：

| 步骤 | 动作 | 关键约束 |
|:--|:--|:--|
| 1 | 参数校验：chunkSize ∈ {256,512,1024,2048}，docId 默认 UUID | — |
| 2 | 登记 `kb_doc` 行（状态 `queued`） | PG 本库 |
| 3 | 原文写 MinIO `raw/unstructured/{source}/{docId}/{fileName}` | 走 runtime-access `MinioStorageService` |
| 4 | REST 登记 datanet `RAW/UNSTRUCTURED/LAKE_OBJECT` | 数据工作台登记端点 |
| 5 | 状态 → `parsing`，调 `DocumentParseService.parse()`（Tika <5MB / MinerU ≥5MB） | runtime-access 公共能力 |
| 6 | 状态 → `extracting`，切分 `kb_doc_chunk`（确定性主键 `UUID.nameUUIDFromBytes(docId#chunkIndex)`） | 批量 upsert |
| 7 | 向量化（`KnowledgeVectorWriteService.upsertVectors` 走 llm-gateway）→ 状态 `done` | 失败不阻断（chunk 已落库，向量待补） |

**A3 边界红线**：
- 禁直写 DW 层 CURATED 表（A2 永久否决）
- 原文写近源层走 runtime-access（禁 new Driver）
- 解析复用 `DocumentParserService`（与 data-engine `TRANSFORM_DOC_PARSE` 共用同一实现）
- **退出条件**：SOURCE_MINIO + 文档解析节点落地后 1 批次内，切分/落库职责迁出至数据工作台

#### 6.2.8 本体映射契约消费逻辑

- **唯一入口**：`GET /api/v1/ontology/entity-mappings?ontologyId=`（REST 跨引擎）
- **不自行推断**：映射数据不落 kb 表（仅内存装配），本体工作台改映射 → 下次抽取即生效
- **字段解析**：`entityCode` / `datasetId` / `resourceName` / `materialized` / `fieldMappings[]`
- **materialized 裁度**（Q2）：`null`/空/`"true"`/`"1"` 视为 true，其他 → skip（issue `Q2_SKIP`）
- **C4 三道闸在 kb 侧重新校验**：DW 资源存在性 → 列定义存在性 → 字段映射命中物理列

#### 6.2.9 数据流向全景

```
ontology-engine (金·I, :18083)
  发布 ontology.publish 事件
        │
        ▼
kb_ontology_snapshot (PG, B1)
  (ontology_id, version, entity_codes[], relationship_codes[])
        │
ontology-engine 提供映射契约
  GET /ontology/entity-mappings?ontologyId=
        │
data-engine (土·D, :18082)
  GET /engine/data/layers/CURATED           ← DW 资源索引
  GET /engine/data/layers/CURATED/          ← 实例行（watermark 增量）
        resources/{id}/rows?watermark=&limit=500
  GET /datanet/metadata/fields/{id}         ← 列定义
        │
        ▼
KbEntityInstanceExtractionService.extract()
  C1 类型存在性 → C2 关系合法性 → C3 属性完整性 → C4 映射有效性（三道闸）
  分页 500×≤200 → 幂等 upsert graph_node / graph_edge
  溯源 4 列：ontology_id / ontology_version / source_resource_id / source_pk
        │
        ▼
ecos_knowledge.graph_node / graph_edge  (K 层知识图谱实例)
  + kb_extract_watermark (增量水位线)
  + kg_sync_log (台账：op/status/nodes/edges/report JSONB)

非结构化旁路（A3 过渡态）：
  文档上传 → MinIO raw/unstructured/ → 解析 → 切分 → kb_doc_chunk
  → 向量化(llm-gateway) → 登记 CURATED
```

#### 6.2.10 引擎边界铁律（代码硬编码遵守）

1. **单一事实源**：DW 层表只读，图谱不回写 DW/本体表
2. **契约先行**：`ecos_entity_table_mapping` 是唯一"表↔实体"入口，不扫表猜
3. **引擎只调 API**：不 import data/ontology/ai-engine impl，REST 跨引擎
4. **任务调度**：走 `ITaskManagementService`（铁律 §1.6），不自建 `ScheduledExecutorService`
5. **审计**：所有写操作发 Kafka `ecos.audit`，Kafka 不可用时 log 兜底
6. **不执行规则判定**（cognitive 的事）/ **不直接调 LLM**（ai-engine 的事）
7. **幂等 upsert**：`ON CONFLICT DO UPDATE` + `RETURNING (xmax = 0)` 区分 create/update
8. **dry-run 真口径**：dry-run 的 created/updated 与真执行同口径（预查存在性集合）
9. **速率防御**：500 × 200 = 10 万行上限
10. **不静默吞**：C1/C2/C4 失败进 `report.issues[]`，不降级放行

### 6.2.11 检索与评估

| 环节 | 物理落点 | 端点 |
|:--|:--|:--|
| RAG 检索 | 向量检索 + 知识问答 | `POST /api/v1/kb/rag` |
| 检索质量评估 | 自检索真实向量（llm-gateway 嵌入 → pgvector `searchByVector`） | `POST /api/v1/knowledge/eval/run`（recall@5/mrr@5/ndcg@5） |
| 资产/审计 | `knowledge_article` + `kg_sync_log` 真实台账 | `GET /api/v1/knowledge/assets` + `lifecycle/audit` |

### 6.3 A3 非结构化过渡态

- 原文写 `raw/unstructured/` → 登记 `RAW/UNSTRUCTURED/LAKE_OBJECT`
- 解析文本经 `TRANSFORM_DOC_PARSE` 落 DW 层 `ecos_dw.doc`/`doc_chunk` 标 CURATED
- **A3 禁令**：kb 不直写 DW 层 CURATED 表（A2 永久否决）
- 退出条件：kb-engine 仍保留 `kb_doc_chunk` 写入路径，下一批次内退出 A3

### 6.4 关键约束

- **Neo4j Driver 合规**：新代码禁止 `new Driver`，必须走 `runtime-access` 的 `Neo4jClient` Bean
- **boot 唯一 `@MapperScan`**（包：`com.chinacreator.gzcm.engine.kb.repository`），**不要扩散**（避免 gateway Bean 冲突）
- **生产走 gateway**，boot 仅开发调试
- 9 个 test class / 67 case

---

## 七、cognitive-engine（木·C）

> 来源: `cognitive-engine/AGENTS.md` + `docs/engine-cognitive/5-cognitive-legacy/` 9 文件

### 7.1 定位与边界

- 认知推理引擎，依赖 kb-engine-api（不依赖 impl）
- 职责：混合推理（KG_QUERY/RULE_CHECK/VECTOR_RAG/HYBRID）/ 因果链推理 / 规则变更影响分析 / 场景模拟 / 统计预测

### 7.2 ADR-9 三档落盘口径（PMO-59 P0，2026-09-14 正式落盘）

| 维度 | 落盘策略 | DDL |
|:--|:--|:--|
| 推理**结果** | **不落盘**（实时计算） | — |
| 模型**资产** | 落盘 | `ecos_cognitive_model`（V124，PMO-51） |
| 认知**心智状态** | 落盘 | `ecos_cognitive_evidence` / `ecos_cognitive_hypothesis` / `ecos_cognitive_belief`（V127~129） |

**不新增其他表；不引入规则引擎（SpEL 即可）**

### 7.3 KAG 决策链（PMO-32~36）

- **KAG（Knowledge-Assisted Graph）不引入 Semantica Rete/Drools**
- 推理链：Builder（抽取）→ Solver（推理）→ 决策落地
- **决策五元组**：Decision / Policy / Exception / Precedent / ApprovalChain
- **统一溯源**：`ecos_provenance_entry`（与 Pipeline 共享同一表）
- OAG 8 步节点：ingest → plan → ontology → extract → build → reason → strategy → decision
- 回滚原则：**只回滚本批 `ecos_provenance_entry` + `ecos_decision*` 写入**，不修改 KG/本体（已落库对象是知识资产，不撤销）

### 7.4 SpEL 规则评估（PMO-35）

- 用 SpEL（Spring 原生，零新增依赖）替换字符串 `contains` 匹配
- **禁引入 Drools/KIE/Semantica Rete**
- 兼容降级：不含 SpEL 的旧 condition 走原 contains 匹配
- `SimpleEvaluationContext.forReadOnlyDataBinding()` + `MAX_FACTS_SIZE=20` 防 OOM

### 7.5 因果链可解释性（5 类 Contract）

- 3 类用户视角（读）：ReasoningPath / ReasoningStep / Justification
- 2 类系统视角（证）：RuleRef / PrecedentRef
- **CausalReasoner 726 行拆解为 5 子组件**（Detector/SuggestionBuilder/RootCauseAnalyzer/PathBuilder/ServiceImpl）

### 7.6 非结构化文档解析决策

- **MinerU 作为可选通道**（不直接替换 Tika）：文件 ≥5MB 或 OCR=true 走 MinerU，否则 Tika
- MinerU 走 HTTP 外部服务（`POST http://mineru:8002 /v1/parse`），**不是 Docker 容器**
- LLM 抽取产物从 2 类扩到 3 类：entity / link / rule
- 实体链接（entity linking）走 ontology-engine REST（不 import ontology-impl）

### 7.7 Pipeline 四件（PMO-36）

- 失败处理：retry N 次（指数退避）→ Fallback（默认 `retry=2, backoffMs=1000, fallback=SKIP`）
- 并行契约：Kahn 拓扑分层，同层 CompletableFuture 并发，线程池 `min(4, 同层节点数)`
- 验证契约：①结构 ②依赖无环 ③节点数 ≤100
- **禁止新建溯源表**——复用 PMO-32 的 `ecos_provenance_entry`

---

## 八、ai-engine（火·W）

> 来源: `ai-engine/` 4 层 AGENTS.md + `docs/engine-ai/2-aispace-legacy/` 14 文件

### 8.1 定位与边界

- Agent 运行时 + LLM 网关 + 知识抽取一体，端口 18084
- 职责：AgentLoopService（**上限 5 轮**）/ ToolExecutorService（SQL/REST/BUILTIN，30s 超时）/ AgentSessionService（30min 空闲过期）/ AgentDelegationService（**单层禁止递归**）/ KnowledgeExtractorService（KAG 抽取）
- 依赖：`runtime/llm-gateway`、`kb-engine-api`、`cognitive-engine-api`

### 8.2 关键禁止项

1. 不直接 import 其他 engine-impl（跨引擎走 REST）
2. 不改 `LLMGatewayService` 接口
3. Agent Loop 上限 5 轮（超过直接截断，不扩顶）
4. 不引入非 Java 依赖（pgvector 不用 Python）
5. Delegation 单层（`delegate_to_agent` 内禁止再委托）
6. LLM 调用不直接调 Provider API，统一走 `llm-gateway`

### 8.3 ai-engine 企业级审计（评分 3.20/5）

| 维度 | 评分 | 关键发现 |
|:--|:--:|:--|
| Tool 系统 | 4.5/5 | 8 内置工具 + 3 执行模式完整 |
| 配置体系 | 4.5/5 | L1→L2→L3 三层覆盖 |
| Agent Loop | 4.0/5 | 缺整体超时 + 真 token 级流式 |
| 会话管理 | 3.5/5 | **无上下文压缩，长会话 OOM 风险** |
| 多 Agent | 3.0/5 | 单层委托可用，缺并行编排 |
| 模型路由 | 3.0/5 | 仅 1 个 Provider，无故障转移 |
| 错误处理 | 2.5/5 | 缺异常分类 + 指数退避 |
| 安全护栏 | 2.0/5 | **代码写了但未接入主流程** |
| 可观测性 | 1.5/5 | 零 trace/metric/alert |

### 8.4 PMO 指令决策记录

| PMO | 关键决策 |
|:--|:--|
| PMO-08 | 可观测性（AgentTracer/AgentMetricsCollector）+ 安全护栏接入 + 会话压缩 + 熔断（连续 3 次失败 → OPEN） |
| PMO-09 | 功能等价拆分：KnowledgeView 2074→6 子组件 / ChatbotStudioView 1613→5 / GuardrailsView 1465→3 / AgentStudioView 1302→3 |
| PMO-10 | i18n 67% 文件未用，~12K 硬编码中文 → `aiworkbench.*` namespace |
| PMO-11 | UX 补齐：排序 0%/分页缺 6/9/确认弹窗缺 5/9 |
| PMO-12 | **ActionType**：ontology-engine 缺"动词"概念，需升级为 Agent 可执行的业务语义层（前置条件引擎 + 后置动作执行器 + 审计强制） |
| PMO-13 | **安全集成**：Ondata OntologySecurityInterceptor AOP 切面，查询前 RLS / 查询后 CLS+脱敏 / ActionType 前 ABAC |
| PMO-14 | **OAG Pipeline**：不替代 AgentLoop，是 AgentLoop 的上层编排；8 步 SSE 流式 |
| PMO-16 | **AIP Evals**：5 维度评分（准确性/安全性/响应时间/幻觉率/工具调用正确率） |
| PMO-17 | **Agent 平台**：Build→Orchestrate→Evaluate→Deploy→Monitor 全生命周期 |
| PMO-18 | **Logic 画布**：拖拽可视化（React Flow），6 种节点类型 |

### 8.5 agent-service 与 ai-engine 的关系

- **ai-engine**（engine 层）：Agent Loop 执行引擎 + 工具执行 + 会话 + 知识抽取 + LLM 网关适配
- **agent-service**（services 层）：Agent 运行时编排 + 工具路由（25 个 Tool）+ 编排/治理/审批/记忆/评估/反思/遥测
- **两者平行**：均通过 gateway 聚合加载，agent-service 不依赖 ai-engine
- M0 改造：移除独立 Spring Boot 启动器，改为 library 被 gateway 依赖

---

## 九、跨引擎共性铁律

> 来源: 所有引擎 AGENTS.md + 架构铁律

### 9.1 禁止清单（全引擎统一）

| # | 禁止 | 原因 |
|:--|:--|:--|
| 1 | 不直接 import 其他 engine-impl | 铁律 2.1，跨引擎走 REST |
| 2 | 不新增 Maven 模块 | 铁律 0.4（基线 13） |
| 3 | 不新开 Docker 容器 | 铁律 0.4 |
| 4 | 不 `new Driver`（PG/Neo4j/MinIO/Git/DuckDB） | 铁律 2.5 #1，收敛 runtime-access |
| 5 | 不直接调 LLM Provider API | 铁律 2.5 #2，走 llm-gateway |
| 6 | 不 `new ScheduledExecutorService` | 铁律 2.5 #3，走 runtime-task |
| 7 | 不自建监控端点 | 铁律 2.5 #4，走 runtime-monitor |
| 8 | 不自建 KafkaTemplate / producer / consumer | 铁律 2.5 公共底座，走 runtime-event |
| 9 | 不硬编码 token / BOD / metadata | 凭据走 application-*.yml + runtime-access |
| 10 | 不用 Flyway（`spring.flyway.enabled: false`） | 数据库访问规范 IR02 |
| 11 | 不 `SELECT *` | IR04 |
| 12 | 不裸 SQL 字符串拼接 | IR05，用 MyBatis `#{}` |
| 13 | 不 `DROP`/`ALTER` 已有表/列 | IR03，R9 只加不删 |
| 14 | 生产发布禁止以 boot 启动 | 生产统一走 gateway |

### 9.2 数据库命名红线（DR01~DR08）

- 新表前缀 `ecos_`（强制）
- JSONB 字段必加 `_json` 后缀
- 布尔字段 `is_` 前缀
- 审计 5 字段固定：`create_time` / `update_time` / `create_by` / `update_by` / `is_deleted`
- 版本字段 `version_no VARCHAR(20) NOT NULL`（乐观锁）
- 多租户预留：`domain VARCHAR(50) NOT NULL DEFAULT 'default'`
- 主键：UUIDv4（新表默认）/ BIGSERIAL（parent-child 性能）
- 索引：常规 `idx_{表名}_{字段}`，唯一 `uniq_{表名}_{字段}`

### 9.3 V150 存量违规处置（2026-09-22 已落地）

| 存量 | 处置 |
|:--|:--|
| `workflow_*` 无 `ecos_` 前缀 | 不 RENAME（knownLegacy 白名单） |
| `created_at/updated_at` 57 文件 218 字段 | V150 RENAME + `v_*` 兼容视图双轨 |
| 13 处 JSONB 无 `_json` 后缀 | 历史不重命名 |
| 96 张新表无 `domain`/`version_no` | 存量不补（V150+ 新表必带） |
| gateway 112 文件上帝模块 | 不加新文件（新 DDL 入 runtime-access） |

---

## 十、三工作台职责边界铁律（v1.3 新增）

> 来源: `架构铁律.md` §0.5

| 工作台 | 核心产出 | 允许读 |
|:--|:--|:--|
| 数据工作台（土 D） | 近源层对象、DW 层表、管道、血缘、DQ | 外部源系统、近源层、DW 层 |
| 本体工作台（金 I） | 本体 schema、**实体→DW 表映射契约**（`ecos_entity_table_mapping`）、版本快照 | **仅 DW 层（只读）** |
| 知识工作台（水 K + 木 C） | 知识图谱实例、向量索引、抽取候选、知识规则 | **仅 DW 层 + 语义层（只读）** |

**五条边界铁律**（违反 = 验收失败）：
1. **单一事实源**：DW 层表写入权只属数据工作台；本体/知识对 DW 层只读
2. **契约先行**：知识工作台不得自行推断映射，必须消费本体工作台的 `ecos_entity_table_mapping`
3. **语义不上移**：数据工作台不得内置业务语义，只做通用存储与加工
4. **图谱不回写**：知识工作台不得把图谱结果写回 DW 层或本体表
5. **映射必校验**：本体工作台保存映射时校验 DW 表/列存在性与类型兼容

---

## 十一、最近优先决策时间线

| 日期 | 决策 | 来源 |
|:--|:--|:--|
| **2026-09-22** | §1.6 即时/定时任务统一接入入口（ITaskManagementService + td_runtime_task_plan） | 架构铁律 v1.3 |
| **2026-09-22** | V150 schema 统一落地（audited 10 节） | 数据库访问规范 v1.0 |
| **2026-09-22** | 文档目录 v2.0 迁移（旧 14 → 新 14，commit 696a5d7..9aa292b） | 文档目录规范 v2.0 |
| **2026-09-19** | §0.5 三工作台职责边界铁律（v1.3 新增） | 架构铁律 v1.3 |
| **2026-09-19** | 数据湖存储分层 v1.2：非结构化 A1 已落地，A3 待退出 | 数据湖分层规范 v1.2 |
| **2026-09-16** | v1.2 三滤波器判据修正（路径重写先于鉴权实证） | 架构铁律 v1.2 |
| **2026-09-14** | ADR-9 认知引擎三档落盘口径正式落盘 | PMO-59 |
| **2026-09-11** | P8-A/B/C 模块结构调整（common→runtime / buszhi→services / sysman→services） | current-plan v1.4 |
| **2026-09-10** | 微服务 v2 改造方案 v1.4（9 ADR + 7 Phase） | current-plan |
| **2026-09-10** | DQ 7 项决策（双轨合并/告警分级/报告双出口/RCA 自动触发/自动修复范围/推送渠道/Kafka） | PMO-48 |
| **2026-09-07** | 项目经验总结 | docs/30-cross-cutting-docs/pmo/ |
| **2026-09-01** | Wave-3B 设计 5 文档（推理可解释/跨引擎编排/非结构化解析/决策智能层/Pipeline 四件） | docs/engine-cognitive/5-cognitive-legacy/ |
| **2026-08-21** | 技术债修复计划（6 条总原则 + 8 决策点 + 阶段 A/A+/B/C/D） | ECOS-技术债修复计划 |
| **2026-08-20** | PMO-32~36（决策智能层 / 跨引擎编排 / 非结构化文档解析 / SpEL+可解释 / Pipeline 四件） | docs/engine-cognitive/5-cognitive-legacy/ |
| **2026-08-07** | 数据源类型保留 6 种不砍 / kb vs cognitive 职责划分明确 | docs/engine-data/3-data-legacy/01-差距分析 |

---

## 十一、横切基础设施决策速查

| 主题 | 决策 | 来源 |
|:--|:--|:--|
| 基础设施访问收敛 | 五类 Driver（PG/Neo4j/MinIO/Doris/Git）统一 runtime-access | 技术债 D5 + 架构铁律 2.5#1 |
| LLM 调用统一出口 | `DEEPSEEK_API_KEY` 仅 gateway 持有，各引擎禁直调 Provider | llm-gateway AGENTS |
| Kafka 统一总线 | KafkaTopics 常量在 common-api，producer/consumer 收敛 runtime-event | 铁律 2.5 公共底座 |
| 审计统一 Kafka | 写操作发 Kafka `ecos.audit`，sysman 消费落库 | 架构铁律 2.4-5 |
| 任务调度统一 | 即时 `ITaskManagementService`；定时 `td_runtime_task_plan` | 架构铁律 1.6 |
| 监控统一 | 各引擎走 runtime-monitor，不自建 Caretaker/Heartbeat | 铁律 2.5#4 |
| 数据隔离三档 | standard=Schema / enterprise+ultimate=独立 Database | ADR-3 |
| 服务间认证 | 在线链 JWT 透传；后台链 `svc-{service}` 账号；gateway strip+重注入 | ADR-7 |
| 生产启动 | 统一 gateway fat-JAR，boot 仅开发调试 | M0 改造 |
| 开发环境 | WSL → Windows 原生（2026-09-09 起） | 根 AGENTS.md |

---

> 文档生成时间：2026-09-22 | 覆盖 ~60 份源文档 | 建议每 Wave 收口后更新本文档
