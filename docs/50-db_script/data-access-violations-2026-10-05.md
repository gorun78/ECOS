# ECOS 数据访问违规台账 + 修复指令（2026-10-05）

> 责任人: AI Agent | 日期: 2026-10-05 | 类型: **指令台账**（交代码 owner 修复，**非本批执行**）
> 依据: `数据库访问规范.md` v1.3（IR01/IR06/ST09/MC05/§0.6）· `架构铁律.md` v2.1 §0.6（确定性计算归属）+ §2.5（runtime 收口）· ADR-14
> 配套: [数据层规范化治理方案](./数据层规范化治理方案-2026-10-05.md) §2.4 归属表 / §2.3 DA01~DA05
> 状态: 所有条目**已源码核实**（`grep` 实测行号），非推测。严重度：P0 阻断 > P1 高危 > P2 中危 > P3 定性待裁。

---

## 〇、结论先行（运行时收口其实已干净）

| 检查项 | 实测 | 判定 |
|:--|:--|:--|
| `new DataSource(` / `new HikariDataSource(` 在 runtime 外 | **0** | ✅ 干净 |
| `MinioClient` 自建 | **1**（V-01） | 🔴 唯一 driver 收口漏点 |
| `DriverManager.*` / `GraphDatabase.driver` / `new KafkaTemplate` 在 runtime 外 | **0**（仅 runtime-access/core/event） | ✅ 干净 |
| 引擎/services 直用 Kafka | **0** | ✅ 干净 |
| MyBatis XML `${}` SQL 插值 | **0**（仅 pom/logback 变量） | ✅ 干净 |
| **ST09 跨引擎写对方 schema 表** | **13 处**（本台账主体） | 🟠 窄口径违规，需仓主域收口 |

> 关键澄清（与用户历史问题对齐）：**「所有数据访问统一经 runtime-data-access」的 driver 收口层面已达纲**（JDBC/PG 池、Neo4j、Git、Kafka 均收敛）。真正残余的是 **ST09 跨引擎 schema 写入**——A 引擎的 Mapper 写裸表名 / 写了 B 引擎或主控制的表。这是逐张表 schema 归属 + 命名（`ecos_` 前缀）问题，不是 driver 问题。

---

## 一、违规明细 + 修复指令

| # | 严重度 | 位置（绝对路径:行） | 违反 | 事实（已核） | 修复指令（交 owner） |
|:--|:--:|:--|:--|:--|:--|
| **V-01** | P1 | `engine/data-engine/data-engine-impl/.../service/DataSourceServiceImpl.java:614-668` | IR06 / 铁律§2.5 / DA02 | 连通性检测时 `io.minio.MinioClient.builder().endpoint(endpoint).build()` + `listBuckets()`，绕过 `runtime-access/MinioStorageService` | **改经 `runtime-access` 的 MinIO 访问门面**（`MinioStorageService` 或补 `MinioProbeService.ping(endpoint)` 封装到 `runtime/access/minio/`）；data-engine 不自建 `MinioClient`。新增 ArchUnit `NoMinioClientOutsideRuntimeAccess` |
| **V-02** | P2 | `engine/cognitive-engine/cognitive-engine-impl/.../repository/CognitivePipelineRepository.java:68`（+ `CognitivePipelineController`/`OpenHealth` 三处读点） | ST09 + §3.1/§0.6 | 读写裸名 `kb_cognitive_pipeline`（`kb_` 前缀 + 无 schema，落在 `public` 残留）；V163 已收编为 `ecos_cognitive.ecos_cognitive_pipeline` | **表名 + schema 双更正**：`kb_cognitive_pipeline` → `ecos_cognitive.ecos_cognitive_pipeline`；`kb_` 前缀是 kb 域命名，cognitive 误借。同步更正 3 处读点 + DDL 单源指向 canonical |
| **V-03** | P2 | `engine/cognitive-engine/cognitive-engine-impl/.../service/CognitiveConfigQueryService.java:64` | ST09 | cognitive 引擎直读 `sys_config`（**sysman 域**·主控制），未经 sysman api 门面 | **改经 sysman api 门面读 `sys_config`**（铁律 §3.6 配置只经 sysman 门面）；cognitive 不直连主控制配置表 |
| **V-04** | P3(定性) | `engine/cognitive-engine/cognitive-engine-impl/.../service/DecisionServiceImpl.java:41,62,230` | §3.1 v2.0 §0.6 / ADR-9 落盘三档 | 写裸 `ecos_decision` / `ecos_decision_causal_link` / `ecos_provenance_entry`（DDL V103，public 无主前缀）——**疑似认知推理「计算产物」落盘** | **需 owner 裁口径**：若属「领域确定性计算产物」→ 按 §0.6 落**业务域 `ecos_dw`/`ecos_cognitive` 应用层**经 data-engine 写通道；若属「即时推理结论」→ 按 §3.3 不落盘。当前 public 无主前缀 = 已违反 schema 归属，须落一个明确归属 |
| **V-05** | P3(定性) | `engine/cognitive-engine/cognitive-engine-impl/.../mental/CognitiveInvalidationConsumer.java:166` | §0.6 白名单邻近 | 写 `ecos_cognitive_run_invalidation`（V130 DDL 存在，心智状态族） | 补登记入 §0.6 cognitive 白名单（evidence/hypothesis/belief/model/pipeline 之外新增心智态），或归并既有四表；不需新 schema |
| **V-06** | P2 | `engine/kb-engine/kb-engine-impl/.../repository/ComplianceRuleMapper.java:30,41,52` + `service/ComplianceRuleVersionService.java:36` | ST09 | kb 引擎写裸 `sys_compliance_rule` / `sys_rule_version`（`sys_` 前缀，**sysman 域**·主控制） | `sys_*` 是 sysman 域表。若合规规则属 **KB 域业务对象** → 改名 `ecos_knowledge.ecos_kb_compliance_rule`（ST07 kb schema）；若确属平台规则配置 → 经 sysman 门面。二者择一，owner 断 |
| **V-07** | P2 | `engine/kb-engine/kb-engine-impl/.../profile/KbProfileStatsMapper.java:26` + `assumption/KbAssumptionValueMapper.java:23` | ADR-14 待裁 | kb 引擎**直接写业务域** `ecos_dw.ecos_kb_profile_stats` / `ecos_dw.ecos_kb_kb_assumption_value`（方向对——计算产物落业务域；但穿透引擎直写，通道越权） | **改经 data-engine REST 写通道**落 `ecos_dw`（铁律 §0.6 第3款 + DA05）；方向合规、通道不合规 |
| **V-08** | P2 | `engine/ontology-engine/ontology-engine-impl/.../repository/LineageEventRepository.java:68` | ST09 | ontology 引擎写裸 `kb_lineage_event`（`kb_` 前缀，**kb 域**·ontology 域外） | 血缘属**土 D**（data-engine）：`kb_lineage_event` → 落 `ecos_data.ecos_data_lineage_*` 或数据域；ontology 不产血缘事实 |
| **V-09** | P2 | `engine/ai-engine/ai-engine-impl/.../agent/mesh/knowledge/repository/KnowledgeNodeRepository.java:38` + `KnowledgeEdgeRepository.java:9,16,21` | ST09 / 双表并存 | ai 引擎写裸 `ecos_knowledge_graph_node` / `ecos_knowledge_graph_edge`（无 schema 前缀落 public）；kb 引擎自有 `ecos_knowledge.graph_node` / `graph_edge`——**疑似知识图谱双写两表** | **收口到 KB 单表**：ai 引擎读知识图谱应走 `ecos_knowledge.graph_node/edge`（kb 域，经 kb REST 或 ST09 授权只读）；删除并表 `ecos_knowledge_graph_node/edge`（若确为 ai 侧私有副本则改名 `ecos_ai.` 前缀并说明隔离理由，owner 断） |
| **V-10** | P3 | `engine/data-engine/data-engine-impl/.../scheduler/DqScheduledTask.java:167` | ST09 | data 引擎调度任务写裸 `sys_config`（sysman 域） | 经 sysman api 门面读写配置（铁律 §3.6） |
| **V-11** | P3 | `engine/security-engine/security-engine-impl/.../service/SecurityAssetCatalogService.java:127` | R9（knownLegacy 停写） | 写 `ecos_security_asset`（knownLegacy `ecos_security` schema）——ST07 允许 security 落自身 schema，但 `ecos_security` 已定性 knownLegacy「只停写不新建」 | **新表按 ST07 收口**：security 新控制数据落 `ecos_security`（五引擎枚举内 security 属护横切，实际按规范主控制/schema 视裁定）；关键 = 不新增到 knownLegacy 已冻结集合之外的**新表**，此条以「停建新表、只读既有资产」为准 |
| **V-12** | —(文档) | `engine/ai-engine/agents.md` 称 `Neo4j*` import `org.neo4j.driver.*` | 文档滞后 | 实测代码已无 driver import（GraphDatabase.driver 仅 runtime-access `Neo4jConfig:46`） | **更新 agents.md** 文档口径（非代码违规），标 Neo4j 走 runtime-access |
| **V-13** | P3(备查) | `engine/data-engine/data-engine-impl/.../adapter/olap/DorisAdapter.java:33` + `ClickHouseAdapter.java:19,28-34` | 治理备查 | 引擎侧拼 JDBC URL + 驱动类名，建连经 `JdbcAccessBridge`（合规，未自建池）；URL/驱动拼装**是否下沉 runtime-access** 由 owner 定性 | **建议下沉** URL/驱动拼装到 `runtime-access`（DA02 收口灵性），非必须 |

### 1.1 干净复核通过项（供 Reviewer 举证）
- **§§ runtime driver 收口**：prod 零自建 `DataSource/MinioClient(裸)/DriverManager/GraphDatabase.driver/KafkaTemplate`（MinIO 唯一漏点 V-01 除外）。
- **§ SQL 注入**：MyBatis XML `INSERT/UPDATE/SELECT` 中 `${}` 零命中（仅 pom/logback 变量非 SQL）。
- **§ Kafka 收口**：全仓唯一 `new KafkaTemplate` 在 `runtime-event/KafkaBusProducerConfig.java:54`（合规）。
- **§ `public.ecos_knowledge_document`**（MC05/ST08 历史缺口）：prod 写入路径 = 0，仅测试基线引用（`kb-engine-impl/DrBaselineColumnsTest.java:62`）。

### 1.2 已知基线（登记非本批违规）
- `JdbcTemplate` production 使用 = **228 文件**（gateway 12 = 全 flag / engine-impl 142：data44·ontology28·kb23·ai22·cognitive16·security9）→ DA01 记 knownLegacy 基线，**新写 = FAIL**；workspace `agents.md` 记的「F07-19 JdbcTemplate 14 类→Mapper」批次实测未落地（workspace-impl 18 处）。
- `SELECT *`（IR04）：当前 **34 处**（data-engine mapper 6 + sysman `-sql.xml` 28），存量基线，分批收敛。
- **已有反向护栏**（可作修复验证锚点）：`data-engine-impl/NoDriverManagerArchTest.java`（白名单 = runtime-access）、`DatasourceSingleSurfaceArchTest.java`（正则锁 `JdbcTemplate|DriverManager`）。

---

## 二、ST09 收口的统一判据（修复时套用）

1. **谁拥有这张表？** 按 `.trae/rules/架构铁律.md` §0.3 六引擎表 + §0.5/§0.6 工作台边界，先定 owner schema。裸表名 / `kb_`/`sys_`/`dw_` 前缀表若写在非 owner engine = 违规。
2. **归属三分**：
   - 控制域定义态（本体/KB/Agent/认知**定义**）→ 落对应五引擎 schema；
   - **业务事实 / 确定性计算产物** → 落业务域 `ecos_dw`，**经 data-engine REST 写通道**（禁引擎直写）；
   - 主控制（IAM/配置/字典/审计）→ 落 `ecos_control`（现 `public`），**经 sysman 门面**。
3. **修验证**：`db-migration-lint.ps1` 0 FAIL + 新增 `st09-crossEngineSchemaWriteGuard` ArchUnit 对修复点转绿 + 对应模块 `mvn -o -pl <module> test` 绿。

> **执行边界**：本台账是**指令**，代码修改交各分册 owner（data/kb/ontology/ai/cognitive/security 各自窗口）。本批（数据层规范化）不改这些业务代码，避免与并行窗口 in-flight 争用。

---
<!-- 数据访问违规台账 / 2026-10-05 / 13 项已源码核实 / 交 owner 分派 -->