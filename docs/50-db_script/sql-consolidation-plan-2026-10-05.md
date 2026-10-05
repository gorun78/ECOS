# ECOS SQL 资产归集方案（canonical-ddl 落位细则）

> 责任人: AI Agent | 日期: 2026-10-05 | 状态: **待 [G1 确认](./数据层规范化治理方案-2026-10-05.md#六需用户逐项再确认的授权裁决项p6-收口前)**
> 用途: [主方案](./数据层规范化治理方案-2026-10-05.md) §三 的 P3 落地细则

---

## 一、目标目录与归属映射

按 [reconciliation-manifest-2026-10-05.md](./reconciliation-manifest-2026-10-05.md) 的 260 声明 / 180 both / 80 声明未灌 / 409 现网未声明 交叉，映射如下：

| 现有 SQL 目录（原文本） | 数量 | 归集落点（canonical/ddl/） | 备注 |
|:--|:--:|:--|:--|
| `gateway/src/main/resources/db/migration/V*.sql` | 197 | **按 CREATE TABLE schema qualifier 拆到**：`10-control-main/` (public 无主) + `20-engine-{data,ontology,kb,ai,cognitive}/` (五引擎) + `30-business-dw/` (ecos_dw) | 目录留 `RETIRE.txt` 指向 canonical；`V{n}` 头注释保留作历史戳 |
| `sysman/impl/sysman-boot/src/main/resources/db/migration/V*.sql` | 16 | 与 gateway 同拆（workflow 回 `20-engine-ontology/`；sys_config/dict/agent/marketplace 回 `10-control-main/`） | **14 个与 gateway 同名不同内容**（V1~V13+V1.1）→ 合并时以 gateway 为默认真源，sysman-boot 版本作**落库证据**附注 |
| `sysman/impl/sysman-boot/database/{V1.4,V1.5,seed.sql}` | 3 | → `40-legacy-frozen/onetime/V1.4__agent_mesh_pg.sql` + `V1.5__decision_case.sql` + `99-seed/99 sysman-boot.sql` | V1.4/V1.5 是 agent-mesh 一次性 bootstrap，non-replayable，归档即可 |
| `ecos_backend/database/{V150__schema_unify,V150__rollback,seed}.sql` | 3 | `40-legacy-frozen/onetime/V150__schema_unify.sql` + 同名 rollback + `99-seed/99-main-legacy-seed.sql` | 一次性 RENAME+视图，non-replayable |
| `ecos-sql/postgresql/{00_init,01~08}*.sql` | 9 | `99-reference-snapshots/8split-knownLegacy/postgresql/`（保留可读） | PG 全量快照，作 knownLegacy 参考；mysql/oracle 分支同归 `dialects/{mysql,oracle}/`（多库兼容对照样本，标注「示意态，现网不跑」） |
| `ecos-sql/migration/12_to_8_schema.sql` | 1 | `40-legacy-frozen/onetime/12_to_8.sql` | 一次性 SET SCHEMA，non-replayable |
| `engine/ai-engine/…/db/migration/V4__add_thread_id.sql` | 1 | `20-engine-ai/V4__add_thread_id.sql`（并入 ai 族，消除第四套散落） | ST10 单源违规收编 |
| `engine/ontology-engine/…/db/ecos_action_type.sql` | 1 | `20-engine-ontology/ecos_action_type.sql`（并入 ontology 族） | ST10 单源违规收编 |
| `gateway/src/main/resources/db/seed/{enterprise_ontology_demo,ecos_demo_dw_schema}.sql` | 2 | → `99-reference-snapshots/seed-knownLegacy/`（演示 seed 归档，不重放） | 非核心 seed |
| `ecos_backend/scripts/*.sql` + `scripts/migration/*.sql` | 7~8 | `99-reference-snapshots/ops-diagnostics/`（含 `ecos_demo_repair_escape_172.sql` 13.3MB 数据修复 blob） | 运维脚本，只读 |
| `ecos-docker/postgres-extensions/vector--0.8.1.sql` | 1 | `00-init/01_extensions.sql`（引用不复制；扩展已装于容器） | 环境初始化 |
| `docs/40-实现/ops/legacy-11/rollback-ddl-pmo58.sql` | 1 | **保留原位**（docs 归档证据区，不搬） | R9 只增不删 |

---

## 二、执行步骤（每个 commit 独立可回退）

> **前置判空**：`git status --numstat` 避开并行窗口 in-flight 文件（`WAgentDdlShapeComplianceTest.java`（分册10，暂不修）· `gateway/routing/*`（并行窗口新建）· `workspace-service/config/*`（并行）· 前端 `main.tsx/Sidebar.tsx`（并行））。**本归集不动前端、不动 gateway/routing/**。

1. **commit 1**：建 `ecos_backend/canonical/ddl/` 骨架 + `README.md` 权威声明 + 6 主目录占位 + `canonical-baseline/captured-live-2026-10-05/` (README/TSV/full-schema-only.sql)
2. **commit 2**：`git mv` gateway 197 → canonical 对应族（按 qualifier 拆）**+ 原目录留 `RETIRE.txt`**（指向 canonical 且声明 knownLegacy 冻结）
3. **commit 3**：`git mv` sysman-boot 16 + database 3 + ai-engine V4 + ontology 单表 → canonical 对应族 + 原目录留 `RETIRE.txt`
4. **commit 4**：`git mv` ecos-sql 三方言 25 + 12_to_8 + V150 一次性 → `99-reference-snapshots/8split-knownLegacy/` + `40-legacy-frozen/onetime/`
5. **commit 5**：`git mv` `gateway/db/seed/{enterprise_ontology_demo,ecos_demo_dw_schema}.sql` + `scripts/*.sql` → canonical 参考区
6. **commit 6**：**规则改写**（见 §三）：`架构铁律.md` §3.1 单源指向 canonical + `数据库访问规范.md` v1.4 新增 ST10/DA01~05 + `AGENTS.md`/`ecos_backend/AGENTS.md` 单源路径
7. **commit 7**：`db-migration-lint.ps1` 6 roots 换 canonical + ~25 硬编码 DDL 走查测试加 `@Disabled("migrated to canonical-DDL; see docs/50-db_script/")` 注解

> **每一步 commit**：本地 commit，**不 push**（延续「离线交付·local only」）；每步单独可 `revert`。

---

## 三、规则改写（P5 交付，随 commit 6 落地）

### 3.1 `.trae/rules/架构铁律.md` §3.1（单源改写）

原文「DDL 单源目录 = `ecos_backend/gateway/src/main/resources/db/migration/`，禁分域另立」改为：

> **DDL 单源目录（v2.2 更新, 2026-10-05）**：DDL 单一真源 = `ecos_backend/canonical/ddl/`（v2.2 新增，取代 gateway 单目录口径）。**任何 `*/src/main/resources/db/migration/` 下新增 `V*n__*.sql` = 违规**（数据库访问规范 ST10）；旧 `gateway/…/db/migration/` 与 `sysman-boot/…/db/migration/` 转 knownLegacy 冻结（只读不新写，物理留 `RETIRE.txt` 指向 canonical）。原「30 个历史 Droptable 例外」「保险版 DDL 目录例外」条款继续保留；单源违规收编完成清单见 `docs/50-db_script/数据层规范化治理方案-2026-10-05.md` §三。

### 3.2 `.trae/rules/数据库访问规范.md`（v1.3 → v1.4 新增）

**新增红线**：
- **ST10（DDL 单源升级版）**：DDL 单一真源 = `ecos_backend/canonical/ddl/`；`*/src/main/resources/db/migration/` 下**新增** `V*n__*.sql` = FAIL；旧目录 deemed-knownLegacy（只读）。Reviewer 静态检 + `db-migration-lint.ps1`。
- **DA01**：数据访问必走 MyBatis 三层（Data/Service/Controller）；引擎/gateway 生产路径 `JdbcTemplate` 存量（228 文件）= knownLegacy 基线，**新写 = FAIL**。
- **DA02**：MinIO/Neo4j/Doris/ClickHouse/Git 全只经 `runtime-access`；Kafka 全只经 `runtime-event`；**生产路径自建 driver/producer 一律 = FAIL**（V-01 唯一漏点，需 owner 修复）
- **DA03**：主控制 schema 名只从 `ecos.db.control-schema` 配置读（MC06 前置），Java 硬编码 `public.`/`ecos_control.` 前缀存量（3973 处）= knownLegacy，**新增 = WARN 起 → 逐步 FAIL**
- **DA04**：新表 OD 生命周期三件套（「CREATE 时同步 canonical/99-seed + 单测护栏 schema 落位 + 列入 db-migration-lint 白名单」），缺一 = 不入库
- **DA05**：业务事实/确定性计算产物必落 `ecos_dw`（对齐 铁律 §0.6 v2.0 第3款 + ADR-14）；cognitive/kb/data/ontology 引擎**禁直写** `ecos_dw`，须经 **data-engine REST 写通道**

**红线条款总数**：由 30 条(IR06+DR08+EN03+ST09+MC06=32) → **37 条**（+ST10 +DA01-05）；ST03-A 解除登记条款不计。

### 3.3 `.trae/rules/架构铁律.md` §2.5 runtime 补充

在 §2.5 项 6（PG 统一访问）下补一句：

> **MinIO driver 收口**（v2.2 补，2026-10-05）：`MinioClient` 只从 `runtime/access/minio/MinioStorageService` 出；引擎内自建 `MinioClient.builder()` = FAIL（**唯一现存漏点** = `data-engine-impl/…/DataSourceServiceImpl.java:662-668` 连通性检测，交 data-engine owner 修复，见 `docs/50-db_script/data-access-violations-2026-10-05.md` V-01）。

---

## 四、db-migration-lint.ps1 改造要点

- `$sqlRoots` 数组（当前 6 roots: `database/gateway-…/db/migration/engine/services/runtime/workspace`）→ 换成 `ecos_backend/canonical/ddl/*`（7 子目录：00-init / 10-control-main / 20-engine-*×5 / 30-business-dw / 40-legacy-frozen / 99-seed）
- 单源判定改为「仓内 DDL 全落 `canonical/ddl/**`，其它目录下的 `V*n__*.sql` = WARN（新增 = FAIL）」
- knownLegacy 白名单扩列：`gateway/…/db/migration/` / `sysman-boot/…/db/migration/` / `engine/ai-engine/…/db/migration/` / `engine/ontology-engine/…/db/` / `ecos-sql/`（R9 只增不删，knownLegacy 可保留原路径可读）
- 基线：现网 `0 FAIL / 8 WARN` → 归集后同口径（预计 WARN 会因「旧目录 knownLegacy 标注」下降）

---

## 五、~25 硬编码 DDL 走查测试的处理

因 G5（未确认）决定是「@Disabled 保 green」还是「改路径重写」，本方案先按 **@Disabled + 迁移注**（保 green 零回归，M5 独立批次再重写）：

```java
// 头部：
// @Disabled("migrated to canonical-DDL (docs/50-db_script); path rewrite batch pending")
@Disabled("MIGRATED to canonical-DDL; log: see docs/50-db_script/数据层规范化治理方案-2026-10-05.md G5")
class AuditColumnPresenceTest { ... }
```

覆盖（16 类）：
- data-engine-impl：`AuditColumnPresenceTest` / `AuditColumnPresenceExtendedTest` / `ActiveFlagUniquenessDdlTest` / `BareTableNameRatchetArchTest` / `DatasourceLegacyPasswordMigrationTest` / `DdlComplianceLintTest` / `SchemaInventoryGateTest` / `PipelineDdlSingleSourcePairingTest` / `MigrationBasenameSingleRootArchTest` / `LegacyDebtBaselineRatchetArchTest`（10）
- kb-engine-impl：`Mc02JsonbRetirementTest` / `DrBaselineColumnsTest` / `SqlDialectDerivationTest` / `DocCarrierConvergenceTest` / `VectorFormDegradationTest`（5）
- cognitive-engine-impl：`CognitiveDocPaths`（被 3+ 测试复用，改 helper 即可）
- ontology-engine-impl：`MetricDefinitionConformTest`
- workspace-impl：`RunPurposeColumnTest` / `DecisionRecordRoundTripTest` / `ScenarioSchemaComplianceTest`
- gateway：`MigrationSingleSourceGateTest` / `DdlShapeComplianceTest` / `WAgentDdlShapeComplianceTest`（⚠ 最后这个已有 `toURI()` 未声明 checked 编译错，交分册10 窗口修）

---

## 六、验收门
- [ ] `find ecos_backend -name 'RETIRE.txt' | wc -l` = 4（gateway-migration + sysman-boot-migration + ai-engine-db-migration + ontology-engine-db）
- [ ] `find ecos_backend/canonical/ddl -name 'V*n__*.sql' | wc -l` ≥ 197（全量归集证据）
- [ ] `git log --oneline --stat -n 7 | grep canonical | wc -l` = 7 commit
- [ ] `db-migration-lint.ps1` 改造后跑 15 项 = 0 FAIL（≤ 8 WARN）
- [ ] 规则文件 ST10 + DA01~05 + v1.4 抬头存在

---
<!-- SQL 资产归集方案 / 2026-10-05 / 待 G1 确认 canonical 位置后执行 / 不 push -->