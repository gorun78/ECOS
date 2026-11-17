# V200 Schema 治理 — 代码数据访问违规修正指令

> 生成日期：2026-10-01
> 背景：V200 迁移将 15 schema 收敛为 8 schema，代码中残留的旧 schema 限定引用需同步修正
> 影响范围：Java 源码（生产 + 测试）、Javadoc/注释、配置文件；MyBatis XML 无命中
>
> **v2 补充（2026-10-01）**：发现历史遗留的更深层架构违规——**所有数据访问必须统一经 runtime 的 data-access 收口**（铁律 §2.5 #1 / IR06），但全仓 ~90 个 Service 类绕过该收口点，直接自建 `JdbcTemplate` 并在方法体内内联 SQL（多数还硬编码 schema 限定名）。原指令仅覆盖 schema 引用修正（表层问题），本节新增该架构违规全景审计与按"数据访问收口 point"重写的整改方向。

---

## 〇、架构违规全景（比 schema 引用更深层的问题）

### 违规规则清单

| 规则 | 条款 | 要求 |
|------|------|------|
| **架构铁律 v2.0 §2.5 #1** | 数据访问收口 | 所有数据访问统一经 **runtime 的 data-access** 收口服务层：内部控制域 PG 表 → **MyBatis mapper** 或 **`JdbcTemplateDaoSupport` 子类 DAO**（均来自 runtime-core）；外部基础设施（Neo4j/MinIO/外部 PG/Git/DuckDB）Driver/Client → **runtime-access** |
| **后端开发规范 §三** | 三层分层 | Service 层负责业务逻辑，**禁 JdbcTemplate 直接连**；数据 CRUD 只走 Dao/Mapper |
| **数据库访问规范 §五** | MyBatis Mapper 规范 | Service 层明确标注 **"禁 JdbcTemplate 直接连"**；Controller 层同禁（IR01） |
| **数据库访问规范 IR06** | 基础设施访问 | Engines / Services / runtime 模块**建外部** Driver（Neo4j/MinIO/Git/外部 PG Connector 等）禁自持——统一走 `runtime-access` |
| **数据库访问规范 MC06** | 多库兼容 | schema/库名一律配置化（`ecos.db.control-schema` 等），**DDL/Mapper/yml 禁硬编码 schema 前缀** |
| **后端开发规范 §九.1** | runtime 公共底座 | PG/Neo4j/MinIO 的 Driver/Client 封装统一走 `runtime-access`；MyBatis 装配 / 共享 `JdbcTemplate` Bean 统一走 `runtime-core`；引擎/服务/gateway 禁止各自 `new Driver` / `new JdbcTemplate` |

### 违规规模统计

| 维度 | 数量 | 说明 |
|------|------|------|
| 生产 Java 文件直持 / 注入 JdbcTemplate | **209**（`src/main`，2026-10-01 实测） | 全仓 grep 233 命中，排除 `archive/` / `src/test` / `target/` 后既是 Service 又是 Repository 混合 |
| 其中是 `**Service*.java`（严格 Service 层） | ~120 | 命名口径 — `*Service*` 或 `*ServiceImpl` |
| 其中是 `**Repository*.java`（DAO 层违规形态） | ~55 | 名为 Repository 但内部直接 `JdbcTemplate`，未继承 `JdbcTemplateDaoSupport` 也未用 MyBatis |
| 其中含硬编码 schema 限定名 | **~43** | 在 SQL 字符串中写了 `schema.table` 形式（V200 前合法 schema） |
| 其中使用已废弃 schema（V200 后失效） | **~15** | `public.*` / `ecos_identity.*` / `ecos_agent.*` / `ecos_dq.*`（旧） |
| 使用 `@Value` 动态 schema 变量（部分合规） | 2 | CaliberService / OntologyProposalService，schema 名从配置注入 |

> 说明：**"Service 层缺少归位"** 的口径不是简单的「文件数 = 90」，而是「所有 `private final JdbcTemplate` 应该收敛为 `**Dao` / `@Mapper` 类」，故上列 209 文件全在 R2 范围内（部分 `*Repository` 属已收敛的 DAO 层但用了错工具，同样需下沉到 `JdbcTemplateDaoSupport` 或 MyBatis）。

### 按引擎分布（实测基线 2026-10-01，`src/main` 命中 `private|protected (final) JdbcTemplate` / `@Autowired ... JdbcTemplate` / `JdbcTemplate xxx;`）

| 引擎/模块 | 生产文件数 | 含 schema 硬编码（估） | 违规严重度 |
|-----------|------|------|------|
| **data-engine**（DQ 子系统为主） | **39** | ~15（`ecos_dq.*` 旧 schema） | 🔴 最严重：schema 已废弃 + 分层违规 |
| **kb-engine** | **23** | ~17（`ecos_knowledge.*`） | 🟡 schema 归属合法，但分层违规 |
| **gateway** | **11** | 0 | 🟡 分层违规；gateway 是 fat-jar，的回滚半径大，建议收官 |
| **ontology-engine** | **26** | ~6（`public.ecos_ontology_*` 旧 + 裸名） | 🔴 schema 引用过时 + 分层违规 |
| **services/sysman** | **21** | 0 | 🟡 分层违规（部分已在 CD-1~CD-3 登记） |
| **ai-engine** | **17** | 0 | 🟡 分层违规，无 schema 硬编码 |
| **workspace** | **14** | 0 | 🟡 分层违规，无 schema 硬编码 |
| **cognitive-engine** | **13** | 0 | 🟡 分层违规，无 schema 硬编码 |
| **security-engine** | **10** | ~2（`public.ecos_security_*` 旧） | 🔴 schema 引用过时 + 分层违规 |
| **services/buszhi** | **8** | 0 | 🟡 分层违规（`Workflow*Repository` 7 文件 + 1 其他） |
| **runtime**（common-api/core/task/event/monitor） | **8** | 3（`public.ecos_runtime_*` 旧） | 🔴 schema 引用过时 + 分层违规 |
| **services/agent-service** | **15** | 1（`ecos_agent.*` 旧） | 🔴 schema 引用过时 + 分层违规 |
| **services/datanet** | **3** | 0 | 🟡 分层违规，最小批次，可作样板 |
| **合计（生产）** | **209** | ~43 | — |

> 注：此 209 只计 `src/main`。加上 `src/test` 与 `archive/legacy`，全仓 grep `JdbcTemplate` 命中约 230 文件（test 4-5 个 + archive 4 个）。上表已按文件路径递归、去重后统计。

### 两种违规模式

**模式 A — Service 层直接执行/内联 SQL（绕过 runtime 的 data-access 收口）**

```java
// ❌ 违规：Service 类直接注入 JdbcTemplate 拼 SQL
//    违反 铁律 §2.5 #1 / IR06：Service 禁持 JdbcTemplate，
//    数据访问必须统一经 runtime-core 的 data-access 收口
@Service
public class OntologyDataService {
    private final JdbcTemplate jdbc;  // ← 绕过 runtime 收口（§三 三层 + MC06）
    public List<Map> query(...) {
        return jdbc.queryForList("SELECT ... FROM public.ecos_ontology_data ...");  // ← 还硬编码 schema
    }
}

// ✅ 合规：Service 调 runtime 收口点对应的 Data 层
//    收口点姿势二选一（由 runtime-core 统一提供、集中扫描、共用单例 JdbcTemplate）：
//    ① MyBatis @Mapper 接口，落在 com.chinacreator.gzcm.**.dao
//       —— 由 runtime-core/MyBatisConfig.MapperScannerConfigurer 统一扫描；
//          SQL 集中在 mapper/**/*.xml，schema 前缀走 MC06 配置化
//    ② 继承了 runtime-core 的 JdbcTemplateDaoSupport 的 XxxDao
//       —— 复用 runtime 底座 @Autowired 的共享 JdbcTemplate Bean，
//          自持 SQL 也进 DAO，Service 只调 DAO 方法
@Service
public class OntologyDataService {
    private final OntologyDataRepository repo;  // ← MyBatis mapper 或 JdbcTemplateDaoSupport 子类的 Facade
    public List<OntologyDataVO> query(...) {
        return repo.listBy(...);  // ← SQL 在 dao 层，不写死 schema
    }
}
```

**runtime data-access 收口点具体落点**（本仓 ground-truth）：

| 收口构件 | 位置 | 职责 |
|---------|------|------|
| `MyBatisConfig` | `runtime-core/.../mybatis/config/MyBatisConfig.java` | 唯一定义 `SqlSessionFactory` / `DataSourceTransactionManager`；`MapperScannerConfigurer` 扫描 `com.chinacreator.gzcm.**.dao`，全仓共用一份 MyBatis 装配 |
| `JdbcTemplateDaoSupport` | `runtime-core/.../database/support/JdbcTemplateDaoSupport.java` | 共用 `JdbcTemplate` Bean 的抽象 DAO 基类；raw-SQL DAO 一律 `extends JdbcTemplateDaoSupport`，不 `new JdbcTemplate` |
| `runtime-access`（Driver/Client 封装） | `runtime/runtime-access/` | **外部基础设施**（Neo4j / MinIO / 外部 PG Connector / Git / DuckDB）的 Driver/Client 收口点；**不适用于内部控制域 PG 表访问** |

> **规则口径**：Service/Controller 层禁 `private final JdbcTemplate` 直持 —— MyBatis mapper 接口（DAO 层 = `@Mapper` 接口）或 `JdbcTemplateDaoSupport` 子类（DAO 层 = 具体 DAO 类）是唯一的合规落点。IR06 的"引擎禁 `new Driver`"约束在 **外部基础设施** Driver/Client；内部控制域 PG 表走 MyBatis / `JdbcTemplateDaoSupport`，两者都归 runtime 底座统一提供。

**模式 B — schema 名硬编码（违反 MC06）**

```java
// ❌ 违规：SQL 字符串中硬编码 schema 前缀
"SELECT ... FROM public.ecos_ontology_data WHERE ..."

// ✅ 合规：schema 名从配置注入
@Value("${ecos.db.engine-schema.ontology:ecos_ontology}")
private String schema;
String sql = "SELECT ... FROM " + schema + ".ecos_ontology_data WHERE ...";
// 或更优：通过 search_path + MyBatis，完全不写 schema 前缀
```

### 整改方向（分三档）

| 档位 | 整改内容 | 适用 | 工作量 |
|------|---------|------|--------|
| **R1 — 紧急止血** | 修正已废弃 schema 引用（`public.*` / `ecos_identity.*` / `ecos_agent.*` / `ecos_dq.*` → 新 schema），保证 V200 后 SQL 能跑 | 本文档 §一~§四 原列的 22 个文件 | 小（字符串替换） |
| **R2 — 数据访问收口归位** | 剥离 Service 层 `JdbcTemplate` 注入；数据 CRUD 全部下沉到 runtime 的 data-access 收口点（**MyBatis `@Mapper`** 接口置于 `com.chinacreator.gzcm.**.dao` 由 runtime-core 的 `MyBatisConfig.MapperScannerConfigurer` 统一扫描，或 **`XxxDao extends runtime-core 的 JdbcTemplateDaoSupport`** 承接 raw-SQL 场景）。Service/Controller 一律禁 `private final JdbcTemplate`；SQL 写死 schema 前缀的同步剥离（走 search_path 或由 MC06 配置化补齐） | ~90 个 Service 文件（清单见上文"按引擎分布"） | 大（需按引擎拆批次；建议 data-engine → kb-engine → ontology/security/ai/cognitive → services → runtime → workspace 的顺序，其中 data-engine 同时含 R1 硬伤优先） |
| **R3 — schema 配置化** | 所有 schema 限定名从硬编码改为 `@Value` 配置注入或 search_path 解析（MC06 全面落地） | ~35 个含 schema 硬编码的文件 + 全仓 3973 处限定名 | 超大（工程清单 #6，已登记 P2） |

> **R1 是本次必做**（V200 后 SQL 直接报错）；**R2/R3 是存量技术债**，已由后端开发规范 CD-1~CD-3、VG-1~VG-7 及工程调整清单 #6 登记，需 PMO 拆批次逐步整改。

---

## 〇·补  PMO 批次拆解（R2 落地排期）

> 实测基线（2026-10-01，排除 `archive/` 与 `src/test`，仅 `src/main` 命中 `private|protected JdbcTemplate` / `@Autowired JdbcTemplate` / `JdbcTemplate 字段`）：**209 个生产 Java 文件**跨 13 个模块。批次按「schema 硬伤优先 → 同域合并 → 分层可验证」原则排布，每批独立可交付、可回滚，门禁同批统一。

### 批次总览

| 批次 | 模块 | 生产文件数 | 里程碑 | 依赖 | 前置 | 验收 |
|------|------|------|--------|------|------|------|
| **B0 — R1 止血** | 全仓 22 文件（见 §P0~P3） | 22 | T+0 | — | V200 已上 | `grep "public.ecos_\|ecos_identity\.\|ecos_agent\.\|ecos_audit\." == 0`（非 archive）；gateway fat-jar 可启动，P0 涉及的运行时接口恢复 200 |
| **B1 — data-engine** | data-engine-impl | **39** | T+2~3 周 | runtime-core | B0 | 该域 JdbcTemplate 直持 0；`PipelineRepository` 等已命名的 `**Repository` 全部归位到 `com.chinacreator.gzcm.engine.data.dao`（MyBatis）或 `extends JdbcTemplateDaoSupport`；DQ 子系统 `ecos_dq.*` 旧 schema 全部改 `ecos_data.*` 并走 search_path |
| **B2 — 知识层双引擎** | kb-engine-impl + cognitive-engine-impl | **23 + 13 = 36** | T+3~4 周 | runtime-core, runtime-access（Neo4j 通道） | B0；B1 完成前不阻塞 | 双引擎 JdbcTemplate 直持 0；`PgVectorSupport` 迁入 kb 的 `dao` 层；cognitive 侧 `BeliefStore/EvidenceStore/HypothesisStore` 抽象改由 runtime 承接；`ecos_cognitive.*` 保持 schema 限定但拆到 `@Value` |
| **B3 — 控制域双引擎** | ontology-engine-impl + security-engine-impl | **26 + 10 = 36** | T+4~5 周 | runtime-core, runtime-access | B0 | 两域 JdbcTemplate 直持 0；`SecurityAssetCatalogService` / `OntologyMappingService` 等 P0 文件在本批内直接下沉到 DAO，schema 修正与收口一次到位（避免二次副本 diff）；audit/Kafka 相关保留 raw-SQL 但剥到 `extends JdbcTemplateDaoSupport` |
| **B4 — AI/Agent 域** | ai-engine-impl + services/agent-service + runtime 全域 | **17 + 15 + 8 = 40** | T+5~6 周 | runtime-core, llm-gateway | B0 | `CronJobRepository` / `SkillRepository` 等 `**Repository` 归位；`ToolRouterServiceImpl`（P0-11）在 B0 已改 schema 的前提下，本批剥离 JdbcTemplate；runtime-core/console 侧 8 个 task/alert/monitor 文件统一走 `JdbcTemplateDaoSupport` |
| **B5 — 服务层** | sysman-impl + buszhi-impl + datanet | **21 + 8 + 3 = 32** | T+6~7 周 | runtime-core | B0；与已有 CD-1~CD-3 收敛合并 | sysman 21 文件本次同时消化 CD-1~CD-3 存量；buszhi 的 `Workflow*Repository` 7 文件是本批主体（分片清晰、可拆 4 规则/2 任务）；datanet 3 文件为最小批次，可作收官样板 |
| **B6 — workspace + gateway** | workspace-impl + gateway | **14 + 11 = 25** | T+7~8 周 | runtime-core | B0~B5 全部完成 | workspace `Object*Service` 系 5 文件拆分出 DAO；gateway 11 文件（`QuotaFilter` / `TelemetryService` / `MonitorService` 等）与 runtime-core `AlertRepository` 相关类合并扫描，防止 gateway 直连 DB 与 runtime 冲突；收口后 gateway fat-jar 内的 DB 访问全部源于 runtime-core Bean |

---

## 〇·补二  B-数据域完整性专项（独立于 R1/R2/R3 的横向核查项）

> 触发：ECOS 定位是强数据平台，控制域/业务域二分是架构根基。2026-10-01 用 `sys_man` 实库 + data-engine 源码交叉核查，发现两处**比 schema 更名更优先于 R3 的隐性偏差**。本专项与 B0~B6 正交，不占 R1/R2/R3 编号，独立验收。
> 说明：此前初稿曾把本专项描述为"业务域写通道未打通"，经源码复核**修正**——写入通道在代码侧已完备（见下），真正缺口是「DB 落地执行」与「schema 命名语义」两处。

### 核查事实（sys_man 实测 + 源码，2026-10-01）

**事实 1 — 控制域名号与业务域名号错位**

`sys_man` 实测各 schema 表数：`ecos_control 49 / ecos_data 36 / ecos_ai 33 / ecos_ontology 28 / ecos_knowledge 26 / ecos_cognitive 20 / ecos_infra 6 / ecos_dw 3`。

- `ecos_data`（36 表：`ecos_pipeline_*` / `td_data_*` / `ecos_dq_*` / `td_catalog_*`）装的是**数据引擎自身控制面**（管道 DAG 定义、质量规则、数据源元数据）——按《数据湖存储分层规范 §〇》这些**本就属控制域**，落控制域**位置正确**。
- 但 schema 名叫 `ecos_data`，极易被读者/PMO/新人误读为"数据域"；真正的**业务域数据湖**是 `ecos_dw`，却在实测中只有 3 张 `demo_*` 表（`demo_customer/invoice/supplier`，共 9 行 seed）。
- 后果：B1（data-engine，本次最大批 39 文件）里绝大多数文件读写的是**控制面**而非业务域，若命名不澄清，"强数据平台"的域划分在代码里对用户不可见。

**事实 2 — 业务域写入通道设计完备，但 DDL 未在本机落地**

代码侧 `data-engine-impl` 的写通道**完整存在**（非空洞）：
- `writechannel/DwWriteChannelService.java` — 业务域写编排，含管控（`ECOS-DATA-053/054` 错误码）
- `writechannel/DwSchemaQualifier.java` — MC06 schema 配置化解析（`@Value ${dw.business-schema:ecos_dw}`），**本身就是 §R3 口径的合规样板**
- `writechannel/OlapEngineResolver.java` — OLAP 仅承 CURATED/APPLICATION，控制域禁入湖（MC04）
- `fact/FactType.java` + `fact/dao/{Attribution,Stage,Resource,Cost,ForecastSnapshot}FactMapper.java` — 业务事实五表，目标 `ecos_dw`
- `BusinessDomainWriteArchTest` / `NonOlapControlWriteGuardTest` / `IdempotentWriteReplayTest` — ArchUnit 守门已建

但 `sys_man` 实库 `ecos_dw.` 下 **5 张 fact 表（`ecos_biz_project_attribution` / `ecos_biz_stage_fact` / `ecos_biz_resource_fact` / `ecos_biz_cost_fact` / `ecos_forecast_input_snapshot`）全部 `RELATION DOES NOT EXIST`**，而它们的 DDL 实实在在于 `docs/50-db_script/02-flyway-migration/gateway/V167__biz_fact_model.sql`。

根因：本仓 Flyway 禁用（`spring.flyway.enabled: false`），迁移脚本靠人工执行，**V167 尚未在本机 `sys_man` 跑过**。这是「设计已落地到 DDL 文件、执行侧未跟」的差距——属执行缺口，非设计缺陷。

### 三项处置

| 项 | 动作 | 归属 | 优先级 | 依赖 |
|----|------|------|-------|------|
| **B-D1 — 执行 V167 落地业务域 fact 表** | 在 `sys_man` 上手工执行 `V167__biz_fact_model.sql`（含索引），跑通 `fact/dao/*FactMapper` 的 smoke 用例；把「5 张 fact 表可创可读写」登记为 B1 出口验收的**前置** | data-engine owner | 高（B1 前） | 无 |
| **B-D2 — 业务域最小真实闭环** | 造一条 demo 源 → RAW(MinIO) → CURATED(`ecos_dw.*fact`) 的真数据流，让五层各至少一张非 demo 表带真实行数。验收：`SELECT count(*) FROM ecos_dw.ecos_biz_stage_fact > 0` 且数据源可经 data-engine `fetchDwDatasets()` 前端读到 | data-engine + kb | 中 | B-D1 |
| **B-D3 — `ecos_data` 语义澄清（二选一，PMO 裁决）** | **方案 A（推荐，V200 后成本最低）**：`ALTER SCHEMA ecos_data RENAME TO ecos_datactl`，把"数据域"名号完整让给 `ecos_dw`；同步改 `MyBatisConfig` 的 `typeAliasesPackage` 与所有 `@Value`/`search_path` 引用。**方案 B**：不改名，在《数据库访问规范》ST07 表 + 本文 §一 加显式注脚"`ecos_data` = 数据引擎控制面，非业务域"，并强制 R3 阶段所有 SQL 前缀 schema 限定（禁裸表名） | DB/规范 owner | 中 | 无（可与 B-D1 并行） |

### 与 B 批次的关系

- B-D1 **先于 B1**（否则 B1 收口 39 文件时，写通道目标表尚不存在，测试无凭据）。
- B-D2 建议作为 **B1 的出口验收增项**（不是独立周排在 B1 之后，而是"数据引擎收口时必须证明控制域产出能写真进 `ecos_dw`"）。
- B-D3 与 B1~B6 时间正交，**独立排期**，但应在 R3（schema 配置化全量）之前定案——否则 R3 时 `ecos_data` 的语义已经是"事实"。

### 判定线

- **通过**：`sys_man.` 内 `ecos_dw` 含 5 张 fact 表 + ≥1 张真实数据表；`ecos_data` 的域归属在文档与（改名后）schema 名上一致；data-engine 的 `BusinessDomainWriteArchTest` 全绿。
- **未通过**：fact 表仍缺、或 `ecos_data`/`ecos_dw` 归属在规范文本中仍自相矛盾——此时"强数据平台"的域二分只在 PPT 层成立，代码层不冒号。

---

### 每批标准动作（模板）

1. **建整改分支**：`refactor/data-access/B{N}-<module>-<topic>`，基线 `release/v2.1-alpha`。
2. **建 DAO 骨架**：为本批新增 `com.chinacreator.gzcm.<engine>.dao` 目录（如不存在），按 `MyBatisConfig` 的 `com.chinacreator.gzcm.**.dao` 全局扫描约定即可被自动装配；raw-SQL 场景建 `XxxDao extends JdbcTemplateDaoSupport`。
3. **迁移 SQL**：把 Service 内的 `jdbc.queryForList / update / queryForObject` 字符串原样搬到 mapper XML 或 DAO 方法；**只搬不改 SQL 语义**，schema 前缀一律按 §一 映射表在 B0 已修正后的最新值转写（B0 之前的批次禁用旧 schema 复写）。
4. **替换 Service 依赖**：`private final JdbcTemplate` → `private final XxxDao`；把 `JdbcTemplate` 字段删除。若 Service 同时用 Kafka/事件，保留业务逻辑不动，只替换 SQL 通道。
5. **单测修补**：涉及 `@SpringBootTest` + `@Autowired JdbcTemplate` 的测试类同步换成 DAO facade（B 内测试文件同步列清单，不单独作 PM 批）。
6. **专门 ArchUnit 断言（防回潮）**：在该 module 下新增/复用 `DataAccessArchTest`：
   ```java
   @AnalyzeClasses(packages = "com.chinacreator.gzcm.<module>")
   class DataAccessArchTest {
       @ArchTest static final ArchRule service_must_not_use_jdbc_template =
           noClasses().that().haveName("*Service").or().haveName("*Controller")
                       .should().dependOnClassesThat().haveName("org.springframework.jdbc.core.JdbcTemplate");
   }
   ```
   门禁：所有 `mvn install` 时该测试失败 immediately。
7. **验收门禁**：
   - `grep -rE "(private|protected)[[:space:]]+(final[[:space:]]+)?JdbcTemplate" <module>/src/main == 0`
   - `mvn -pl <module> -am clean install`（含 ArchUnit）
   - Playwright：该模块面向的前端 tab E2E 用例全绿（不阻塞下一次 batch）
   - 追加门禁：`route-rewrite-lint.ps1` 与 `check-legacy-modules.ps1` 基线不变
8. **合单**：每批单一 feature 分支 + 单一 commit（`refactor(data-access): B{N} <module> 收口 JdbcTemplate 到 runtime 底座`）；主干禁直推（Git 提交规范）。

### 风险与回滚

- **风险 1**：`@Transactional` 语义依赖——原有 Service 上的 `@Transactional` 若直接绑在 `JdbcTemplate` 长事务上，迁移到 DAO 后事务边界可能变化。缓解：本批保留 Service 上的 `@Transactional` 不动，仅核对 DAO 方法是否纳入同一事务源（`DataSourceTransactionManager` 已由 `MyBatisConfig` 单例提供，默认带 propagation `REQUIRED`）。
- **风险 2**：跨 schema raw-SQL（如 `SELECT ... FROM ecos_data.x LEFT JOIN ecos_ontology.y`）——B1/B2/B3 会碰到跨 schema join，不额外做 schema 前缀剥离，仅确保两个 schema 均在 `search_path` 覆盖或绝对路径。
- **风险 3**：`Workspace` / `Gateway` batch（B6）横跨运行时——gateway 是 fat-jar，一旦坏一个服务影响所有前端 tab。缓解：B6 内部再拆 `gateway-telemetry` / `gateway-filter` / `workspace-scenario` 三个 sub-batch，各独立可回滚。
- **回滚策略**：单批回滚 = `git revert <feature-branch-merge-commit>`；DAO 骨架保留（不删，避免下一批重复建表）；生产库不做 schema 回退（B0 时的字符串替换不影响表结构）。

### 并行能力

- B1/B2/B3 可**并行**（三个引擎目录解耦，未发现跨引擎 `JdbcTemplate` 静态依赖）——建议同时开三条 feature 分支，PMO 排 3 名后端并行 6 周。
- B4/B5/B6 与前面**串行**（gateway/workspace 依赖前面 DAO 骨架成熟，避免 ArchUnit 冲突）。
- 各批次在 feature 分支期间**互不合并**，避免 reactor 内测试污染与 `ArchitectureTest.baselineModules` 抖动。

### 与既有整改登记的映射

| 本指令批次 | 已登记整改项 | 状态 |
|-----------|-------------|------|
| B0 | 本文档 §P0~P3 + 特殊 | 待启动（用户已授权信息收集，待 GO） |
| B1 | 后端开发规范 CD-1~CD-3 部分；VG-1；工程清单 #6 | 待启动 |
| B2 | VG-2 | 待启动 |
| B3 | VG-3 | 待启动 |
| B4 | VG-4 + agent-service H11-T2 | 待启动 |
| B5 | CD-1~CD-3 全量；VG-5~6 | 待启动 |
| B6 | VG-7 | 待启动 |

> R3（schema 配置化全量）独立排期，预计 B1~B6 完成后启动，需先落地 `ecos.db.engine-schema.*` 配置骨架（当前仅 CaliberService / OntologyProposalService 2 处有 `@Value` 用法可作基线）。
>
> **另见 §〇·补二 B-数据域完整性专项**：强数据平台的控制域/业务域二分存在「`ecos_data` 名实错位 + `ecos_dw` 5 张 fact 表 DDL 未在本机落地」两处横向偏差，与 R1/R2/R3 正交，其中 **B-D1（执行 V167 落 fact 表）须先于 B1**，否则 B1 收口时写通道无目标表可测。

---

## 一、Schema 映射表（V200 权威）

| 旧 schema | 新 schema | 适用表 |
|-----------|-----------|------|
| `public` | `ecos_control` | `ecos_runtime_*`、`ecos_security_*`、`sys_*`、`td_*`、`ecos_biz_*` |
| `public` | `ecos_ontology` | `ecos_ontology_*` |
| `public` | `ecos_cognitive` | `ecos_cognitive_*` |
| `ecos_identity` | `ecos_control` | `td_user`、`td_role`、`td_user_role`、`td_user_organization`、`td_organization`、`td_permission` |
| `ecos_audit` | `ecos_control` | `td_audit_log` |
| `ecos_agent` | `ecos_ai` | `ecos_tool_definition` 等 |

---

## P0 — 生产代码（运行时 SQL 会报 relation does exist，必须立即修复）

### 1. DltConsumer.java
- **路径**: `ecos_backend/runtime/runtime-event/src/main/java/com/chinacreator/gzcm/runtime/eventbus/dlq/DltConsumer.java`
- **行**: 34
- **旧**: `"public.ecos_runtime_event_dlq"`
- **新**: `"ecos_control.ecos_runtime_event_dlq"`
- **同行注释 L20 一并修正**

### 2. SecurityAssetCatalogService.java
- **路径**: `ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/gzcm/engine/security/catalog/SecurityAssetCatalogService.java`
- **行**: 50, 61, 93, 107, 127（全部 `public.ecos_security_asset`）
- **新**: `ecos_control.ecos_security_asset`
- **同行注释 L16 一并修正**

### 3. OntologyDataService.java
- **路径**: `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/service/OntologyDataService.java`
- **行**: 50, 51, 70, 75, 89, 101, 115, 130, 139（全部 `public.ecos_ontology_data`）
- **新**: `ecos_ontology.ecos_ontology_data`
- **同行注释 L12 一并修正**

### 4. OntologyMappingService.java
- **路径**: `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/service/OntologyMappingService.java`
- **行**: 223, 225（`public.ecos_ontology_entity`）
- **新**: `ecos_ontology.ecos_ontology_entity`

### 5. GitArchivePendingRepository.java
- **路径**: `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/repository/GitArchivePendingRepository.java`
- **行**: 18（注释 `public.ecos_ontology_git_archive_pending`）
- **新**: `ecos_ontology.ecos_ontology_git_archive_pending`
- **注意**: 检查该类中是否有实际 SQL 字符串引用此表（grep 仅命中注释行，但需确认）

### 6. OntologyVersionService.java
- **路径**: `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/service/OntologyVersionService.java`
- **行**: 111（注释 `public.ecos_ontology_git_archive_pending`）
- **新**: `ecos_ontology.ecos_ontology_git_archive_pending`
- **注意**: 检查该方法体中是否有实际 SQL 字符串

### 7. AlertRepository.java
- **路径**: `ecos_backend/runtime/runtime-core/src/main/java/com/chinacreator/gzcm/runtime/core/alert/persist/AlertRepository.java`
- **行**: 47, 63, 78, 92, 104, 110, 118, 147（全部 `public.ecos_runtime_alert_rule` / `public.ecos_runtime_alert_record`）
- **新**: `ecos_control.ecos_runtime_alert_rule` / `ecos_control.ecos_runtime_alert_record`
- **同行注释 L20 一并修正**

### 8. GitArchiveRetryTask.java
- **路径**: `ecos_backend/runtime/runtime-task/src/main/java/com/chinacreator/gzcm/runtime/core/ontology/archive/GitArchiveRetryTask.java`
- **行**: 35
- **旧**: `"public.ecos_ontology_git_archive_pending"`
- **新**: `"ecos_ontology.ecos_ontology_git_archive_pending"`

### 9. AuditRetryTask.java
- **路径**: `ecos_backend/runtime/runtime-task/src/main/java/com/chinacreator/gzcm/runtime/core/audit/retry/AuditRetryTask.java`
- **行**: 34
- **旧**: `"public.ecos_runtime_audit_retry"`
- **新**: `"ecos_control.ecos_runtime_audit_retry"`
- **同行注释 L20 一并修正**

### 10. CognitionContractGuard.java
- **路径**: `ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/cognition/CognitionContractGuard.java`
- **行**: 25, 26, 27（常量定义）
- **旧**: `"public.ecos_cognitive_hypothesis"` / `"public.ecos_cognitive_belief"` / `"public.ecos_cognitive_mind"`
- **新**: `"ecos_cognitive.ecos_cognitive_hypothesis"` / `"ecos_cognitive.ecos_cognitive_belief"` / `"ecos_cognitive.ecos_cognitive_mind"`
- **同行注释 L15 一并修正**

### 11. ToolRouterServiceImpl.java
- **路径**: `ecos_backend/services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/toolrouter/ToolRouterServiceImpl.java`
- **行**: 54
- **旧**: `ecos_agent.ecos_tool_definition`
- **新**: `ecos_ai.ecos_tool_definition`

---

## P1 — 测试代码（断言/数据校验会失败，需同步修复）

### 12. SecuritySeedConsistencyTest.java
- **路径**: `ecos_backend/engine/security-engine/security-engine-impl/src/test/java/com/chinacreator/gzcm/engine/security/SecuritySeedConsistencyTest.java`
- **行**: 94
- **旧**: `"INSERT INTO public.ecos_rls_policy"`
- **新**: `"INSERT INTO ecos_control.ecos_rls_policy"`

### 13. CognitionContractGuardTest.java
- **路径**: `ecos_backend/engine/kb-engine/kb-engine-impl/src/test/java/com/chinacreator/gzcm/engine/kb/cognition/CognitionContractGuardTest.java`
- **行**: 27, 28, 29, 30
- **旧**: `"public.ecos_cognitive_hypothesis"` 等
- **新**: `"ecos_cognitive.ecos_cognitive_hypothesis"` 等

---

## P2 — 注释/Javadoc（不影响编译，但文档准确性需维护）

### 14. OntologyDataController.java
- **路径**: `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyDataController.java`
- **行**: 31, 48
- **旧**: `public.ecos_ontology_data`
- **新**: `ecos_ontology.ecos_ontology_data`

### 15. CognitionReadService.java
- **路径**: `ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/cognition/CognitionReadService.java`
- **行**: 16
- **旧**: `public.ecos_cognitive_*`
- **新**: `ecos_cognitive.ecos_cognitive_*`

### 16. KnowledgeStatsVO.java
- **路径**: `ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/knowledge/dto/KnowledgeStatsVO.java`
- **行**: 32, 38
- **旧**: `public.sys_compliance_rule` / `public.sys_rule_version`
- **新**: `ecos_control.sys_compliance_rule` / `ecos_control.sys_rule_version`

### 17. OntologyNoForeignTableAccessArchTest.java
- **路径**: `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ontology/OntologyNoForeignTableAccessArchTest.java`
- **行**: 20
- **旧**: `public.ecos_biz_metric`
- **新**: `ecos_control.ecos_biz_metric`（注释，确认实际表归属后修正）

### 18. application.yml (dccheng)
- **路径**: `ecos_backend/services/dccheng/src/main/resources/application.yml`
- **行**: 83
- **旧**: `sys_man.public.ecos_ontology`
- **新**: `sys_man.ecos_ontology.ecos_ontology`

---

## P3 — 归档遗留（archive/legacy/services-ghost/，不参与编译，低优先级）

### 19. PrivacyService.java
- **路径**: `ecos_backend/archive/legacy/services-ghost/identity-service/src/main/java/com/chinacreator/gzcm/services/identity/service/PrivacyService.java`
- **行**: 26, 32, 33, 39, 49, 52, 53
- **旧**: `ecos_identity.td_user` / `ecos_identity.td_role` / `ecos_identity.td_user_role` / `ecos_identity.td_user_organization` / `ecos_audit.td_audit_log`
- **新**: `ecos_control.td_user` / `ecos_control.td_role` / `ecos_control.td_user_role` / `ecos_control.td_user_organization` / `ecos_control.td_audit_log`
- **说明**: 归档遗留代码，不参与 Maven 编译；若保持原样作为历史快照亦可，建议添加注释标注"原 schema 已废弃"

### 20. MfaService.java
- **路径**: `ecos_backend/archive/legacy/services-ghost/identity-service/src/main/java/com/chinacreator/gzcm/services/identity/service/MfaService.java`
- **行**: 22, 29, 36, 43
- **旧**: `ecos_identity.td_user`
- **新**: `ecos_control.td_user`
- **说明**: 同 P3-19

---

## 特殊处理 — PublicSqlRegressionTest（两个副本）

### 21. gateway/f0214/PublicSqlRegressionTest.java
### 22. gateway/arch/PublicSqlRegressionTest.java

- **路径**: 见上
- **性质**: 这两个测试文件围绕 `public.sql` 文件的存在性/内容做断言。V200 后 `public` schema 已不再是数据表归属，`public.sql` 概念已过时
- **建议**: 审查这两个测试的守门意图是否仍然成立。若 V200 后不再需要 `public.sql` 守门，可整体 `@Disabled` 或删除；若仍需对 `ecos_control.sql` 做类似守门，需重写测试目标

---

## 执行顺序建议

1. **先修 P0**（11 个文件）— 这些是运行时 SQL 字符串，不改则查询必报 `relation "xxx" does not exist`
2. **再修 P1**（2 个文件）— 测试断言值，不改则测试 FAIL
3. **顺手修 P2**（5 个文件）— 注释/Javadoc，不影响运行但保持一致性
4. **P3 视决策**（2 个文件）— 归档遗留，可标注不改
5. **PublicSqlRegressionTest 需评审后决策**

## 验证方法

```bash
# 修正完成后，全仓 grep 应返回 0 命中（排除 archive/）
cd ecos_backend
grep -rn "public\.ecos_" --include="*.java" --include="*.xml" --include="*.yml" . | grep -v archive/ | grep -v target/
grep -rn "ecos_identity\." --include="*.java" . | grep -v archive/ | grep -v target/
grep -rn "ecos_agent\." --include="*.java" . | grep -v archive/ | grep -v target/
grep -rn "ecos_audit\." --include="*.java" . | grep -v archive/ | grep -v target/
```

## 统计

| 优先级 | 文件数 | 修改点 | 影响 |
|--------|--------|--------|------|
| P0 生产代码 | 11 | ~40 处 | 运行时 SQL 查询 |
| P1 测试代码 | 2 | ~6 处 | 测试断言 |
| P2 注释/Javadoc | 5 | ~8 处 | 文档准确性 |
| P3 归档遗留 | 2 | ~11 处 | 无编译影响 |
| 特殊 | 2 | 需评审 | 守门测试存废 |
| **合计** | **22** | **~65 处** | — |
