# 50-db_script — DDL 迁移脚本唯一单源

> 来源: docs 整理批（2026-11-17），用户裁决「归档非单源子目录」+ 数据库脚本整理计划 阶段5（2026-11-18），用户裁决「分两步：先建新，后归档」 | 责任人: 文档整理 Agent
> 宪法依据: `.trae/rules/架构铁律.md` **v2.4 §3.1**（2026-10-11 卷09 H-7 订正落点 + 2026-11-18 阶段5 归档二分）

## 唯一权威

**`02-flyway-migration/gateway/` 是全仓 DDL 迁移脚本的唯一活单源**，自 2026-11-18 阶段5 起**只承载 V350+ 新脚本**（当前为空目录，属预期状态，不是丢失）。

V1~V349 共 **298 枚** `V*.sql` 已整体 `git mv` 归档至 **`../90-归档/50-db_script/02-flyway-migration-legacy/gateway/`**（293 枚 R100 纯改名，内容逐字节零改动；另 5 枚 V345~V349 当时尚未入库，随目录一并物理搬迁）。最新 **V349**；**下一号 V350**。

- 新迁移脚本**只写活单源**，V 号自 **V350** 顺延（下一号以 `40-实现/DDL迁移脚本落地登记-2026-09-30.md` + gateway `MigrationVersionUniquenessTest` 钉值为准，两处同批改）。
- **归档目录只读存证，不是第二 DDL 源，禁写入新脚本**。「禁分域另立目录」语义不放宽。
- 🔴 **门禁/lint 扫描面 = 活单源 ∪ 归档**。只扫活单源 ⇒ 298 枚历史脚本脱离判定面（空集假绿，根因同 H-7）；只扫归档 ⇒ 未来 V350+ 新脚本脱离判定面。点名取单脚本者按「活单源优先、归档兜底」解析。
- `ecos_backend/gateway/src/main/resources/db/migration/` 是空目录，**不写入**（历史误记落点）。
- Flyway 运行时禁用（`spring.flyway.enabled: false`），脚本**落文件不实跑**；对 dev 库实跑属授权动作，须先事务空跑验证。
- 命名与红线遵循 `.trae/rules/数据库访问规范.md` **v1.6**（IR/DR/EN/ST/MC 30 条）；种子幂等写法用 `WHERE NOT EXISTS` / DELETE+INSERT，**不得** `ON CONFLICT`（MC03 + 规范 §seed 订正注）。

## ⚠ 历史漂移警示（为什么其余子目录被归档）

以下子目录已整体迁至 `../90-归档/50-db_script/`，**它们不再是权威**，引用其中任何建表都会造成漂移误判：

| 已归档子目录 | 漂移风险（实测案例） |
|:--|:--|
| `01-ddl-多方言/` | postgresql/00~08 建的是 V200 治理**之前**的旧 schema 形态，与现行 8 schema 封闭枚举不符 |
| `05-service/sysman/`（V1~V41） | 旧 V 号序列与单源 V 号**撞号**；dev 库部分表（demo_customer 等）即由此手建、从未进单源 |
| `04-engine/`、`03-seed/`、`06-docker-infra/`、`07-ops-scripts/`、`08-database-util/` | 历史手建产物；与单源并存时无法判断"哪个才是真身" |
| 根部 `V200__schema_governance.sql` | **同号不同内容**（2026-11-17 实证订正）：单源里的 `V200__ai_tool_registry.sql` 建 AI 工具注册表，归档这枚做的是 schema 治理，两者**不是同一脚本的新旧副本**。原表述「V200 已收录进单源」为误记 —— 单源从未收录 schema 治理那枚，V200 号被两份互不相干的脚本占用 |

**实证教训（D123-2，2026-11-17）**：单源外的 V111 建 `ecos_dq.dq_*`，而 DQ DAO 读写 `ecos_data.dq_*` —— 两套并存导致"全新库建不出 DAO 要的表"误判数周。任何表结构争议，**只认单源 + 活库实查**，不认归档件。

## `00_init/` — 从零建库链路（2026-11-17 新增）

单源 `V*.sql` 是**增量迁移史**，不是建库脚本：298 枚里含大量已被后续脚本改写的中间形态，按号顺序重放并不等于当前库结构。`00_init/` 是从**真实库 `sys_man` 反向导出**（`pg_dump --schema-only` + 清洗装配）得到的当前态快照，用于空库一次建成。

| 文件 | 内容 | 行数 |
|:--|:--|--:|
| `00_create_schemas.sql` | ST07 八 schema 封闭枚举 + `public` 扩展宿主 | 73 |
| `01_ecos_control.sql` ~ `08_ecos_dw.sql` | 按 schema 分文件的建表/索引/约束/注释 | 17564 |
| `09_foreign_keys.sql` | 全库 **36** 条外键，延后统一施加 | 246 |
| `10_system_seed.sql` | 系统初始配置数据 **27 表 / 795 行**（IAM、租户、安全策略、配置字典、各引擎平台级注册表） | 1009 |

**执行顺序固定 00 → 01~08 → 09 → 10**。FK 之所以从建表文件里拆出来单列 09：存在 `ecos_control → ecos_ontology` 等跨 schema 引用，按文件顺序内联必然失败，且无法拓扑排序。种子顺延为 10（原计划 09）。

**空跑纪律**：`10_system_seed.sql` 自带 `BEGIN` + `\if :{?ecos_commit}` 开关，**默认 ROLLBACK**；实跑须显式 `-v ecos_commit=1`。01~09 刻意**不含** `IF NOT EXISTS` —— 结构不符即报错（fail-loud，禁静默跳过）。

**已验证**（2026-11-17，全新空库 `ecos_init_verify3` 实跑全链）：`td_user=17 / loginable=5 / admin_super=1 / td_role_permission=135 / sys_config=178`；`users` 表不存在；重复执行 0 新增行（幂等成立）。

### 登记在案的偏差（反向导出的真实形态，不掩盖）

| 偏差 | 实测量 | 说明 |
|:--|--:|:--|
| MC03 `::` cast | **360** 处（01~08） | `pg_dump` 输出保留形态。清洗只去掉了冗余的 `::character varying`；其余 cast 与列默认值/表达式绑定，强剥会改变语义。种子文件本身 0 处 |
| MC01/MC02 `gen_random_uuid()` | **5** 列 | `01_ecos_control:1357`、`03_ecos_ontology:309`（VARCHAR(64)）、`04_ecos_knowledge:1065/1121`（VARCHAR(36)）、`07_ecos_infra:99`（uuid）。均为**活库既有列**，非本次新增；改成应用侧 UUID 属 DDL 变更，需单独授权 |
| 分区子表 | **60** 张未导出 | 只保父表 + DEFAULT 分区，按月日期子表由运行时创建 |
| `users` 平行误会表 | 整族剔除 **4** 个块 | 登录权威已由 V347 统一到 `td_user`，镜像层已拆除；导出器按表名+表体双判据排除，非手工删 SQL |

`ON CONFLICT` 在 01~10 的**可执行 SQL 中为零**：仅 2 处文本命中，分别是 `03_ecos_ontology.sql:177` 的 `COMMENT ON TABLE` 字符串（描述既有 DAO 行为）与 `10_system_seed.sql:16` 的禁令说明本身。

### `03-baseline/` — 清理前基线

`schema-snapshot-actual.sql` + `data-snapshot-actual.md` 是阶段 3 清理动作的**对照基线与回滚依据**，不是建库脚本，不参与 00→10 链路。

同目录另有两份**状态册**（不是基线快照）：

| 文件 | 作用 |
|:--|:--|
| `plan-completion-audit.md` | **六阶段完成度核账**——批准计划的每个阶段逐项对照实跑证据，含「经反证否决」的计划项。判断「脚本整理这批到底做完没有」看这一份 |
| `dup-family-adjudication.md` | **36 重名表族裁定册**——三口径计数、三证据腿、逐族判定、§七/§八 逐族活体反证与待裁项 |

## 阶段5 — 历史脚本归档与门禁扫描面同批改（2026-11-18，状态单源）

用户裁决：「分两步：先建新，后归档」+「现在做，按你的方案直接执行」（含门禁路径同批改与实跑复验）。

### 落地动作

| 动作 | 实测结果 |
|:--|:--|
| `git mv` 整目录（保留 `gateway/` 层级） | `docs/50-db_script/02-flyway-migration/gateway` → `docs/90-归档/50-db_script/02-flyway-migration-legacy/gateway` |
| 改名纯度 | `git diff --cached -M --name-status` = **293 枚 R100**（100% 相似 = 内容逐字节相同），零内容改动 |
| 归档目录实扫 | **298** 枚 `.sql`，最大版本 **V349** |
| 活单源重建 | 空目录（预期状态），保留供 V350+；`SourceNotIgnoredGateTest` 的探针写盘门仍以此为锚 |
| Java 门禁同批改 | **51 个文件**（gateway 13 + cognitive/ontology/data/kb/ai/security/common-api/runtime-access/agent-service/workspace-impl 38） |
| `_win_tasks/db-migration-lint.ps1` | 增归档扫描根（属主改动，用户已批准；只增不改红线的例外授权） |

**改法分四类**（避免一刀切造成假绿）：**A** 点名取单脚本 → 前缀替换 + 活单源优先/归档兜底；**B** 整目录枚举 → 必须并集；**C** 仓根锚（`Files.isDirectory(.../02-flyway-migration/gateway)`）与探针写盘门 → **不动**（目录仍实存）；**D** Javadoc/注释散文 → 随文订正。

### 实跑复验（真实命令，非 skipTests）

跨 11 模块 `test-compile`：**BUILD SUCCESS**（6:29 min）。

44 枚受影响门禁实跑（`mvn -o -f ecos_backend/pom.xml test -pl <11 模块> -Dtest=<44 类名>`，类名先逐个核实实存，避免 `failIfNoSpecifiedTests=false` 把拼错静默跳过）：

| 模块 | Tests run | Failures | Skipped |
|:--|--:|--:|--:|
| common-api | 11 | 0 | 0 |
| runtime-access | 4 | 0 | 0 |
| security-engine-impl | 6 | 0 | 0 |
| data-engine-impl | 32 | 0 | 0 |
| ontology-engine-impl | 9 | 0 | 0 |
| ai-engine-impl | 11 | 0 | 0 |
| cognitive-engine-impl | 39 | 0 | 0 |
| kb-engine-impl | 10 | 0 | 0 |
| agent-service | 7 | 0 | 0 |
| gateway | 73 | **2** | 2 |
| **合计** | **202** | **2** | **2** |

10 模块 SUCCESS，gateway FAILURE。**钉值全部守住**：`MigrationVersionUniquenessTest` 5/5 绿（`SCRIPT_COUNT_FLOOR=278` / MAX 349 / NEXT 350 未动）、`MigrationSingleSourceTest` 绿、`DdlShapeScanner` 三枚（DrBaselineColumns 7 / Mc02TypeWhitelist 6 / Mc03TimeForm 6）全绿。2 枚 skip 是既有 `@Disabled`（`ecos-sql/postgresql/public.sql` 不存在），与搬迁无关。

### 2 枚红 = 存量，已反证归因（本批未引入、也未掩盖）

**反证链**（不是推断，是实证）：① 两枚门禁对 HEAD 的 diff **只有路径/扫描管路，钉值与名录零改动**；② 293 枚 R100 证明内容逐字节相同；③ `DdlCodeSchemaAlignmentGateTest.analyzeRepo` 按**文件名→内容**建 map、`analyze` 再按 `compareScriptVersions` 排序 ⇒ 判定结果是文件集的**纯函数**，与目录布局/枚举顺序无关。三者合起来 ⇒ 搬迁不可能改变这两枚的判定。

| 红 | 实测归因 | 处置 |
|:--|:--|:--|
| `DdlCodeSchemaAlignmentGateTest.duplicatedTableNamesAreLimitedToRegisteredPatterns`：期望 3 个同名表，实测 13 个 | **11 个 `dq_*` 入集** = `V111__ecos_dq_governance_v1.sql` 建 `ecos_dq.dq_*` 与 `V342__ecos_data_dq_tables.sql` 建 `ecos_data.dq_*` 并存（真实读写分叉）；**`ecos_ontology_entity` 出集** = `V349:165` `DROP TABLE IF EXISTS public.ecos_ontology_entity`（本计划阶段3 已授权清退） | **不扩 `ALLOWED_DUPLICATES` 换绿**。11 个 dq 族属「逐族按 DDL 意图裁定」待办面，须先裁清落点再登记；`ecos_ontology_entity` 的出集是授权清退的真实结果，待该族一并订正钉值 |
| `OntologyNoFactValueArchTest.moneyScaleNumericSurfaceIsPinnedAndNeverOntological:320`：钉值名单缺 **V344__ecos_business_scenario_budget_backfill.sql** | V344 早在归档前就实存于单源，钉值名单从未收录它（既有登记缺口，先前已登记在案） | 保持红。补登前须先核 V344 的 `NUMERIC(p,s)` 落点确在 `ecos_dw` 而非本体面，否则补登等于掩盖违例 |

### lint 基线（诚实登记，不沿用旧口径）

`_win_tasks/db-migration-lint.ps1` 在 **298 枚**语料下实跑 = **1 FAIL / 7 WARN，EXIT=0**。

- 旧文档基线「0 FAIL / 8 WARN」测于 2026-10-12 的 **231 枚**语料，**不可直接沿用**。
- 归档根确被扫到的反证：`DR02 真新增表 73 张`、`MC01~MC03 存量 146 处` 均非空集（若只扫空的活单源必为 0）。
- 唯一 FAIL = **IR03「禁 DROP/ALTER」42 处**，逐脚本归因：`V126`×8（已豁免）+ `V315`×3 + `V323/V324/V325`×各1 + `V349`×36 = 50 命中，50 − 8 = **42**。V323~325 是设计资产历史版本表 DROP（铁律 §3.1 授权例外），V349 是本计划阶段3 已授权清退，V315 属历史存量。
- IR03 豁免按**文件名**匹配（`$dgAllowed = @($dg | Where-Object { $_ -match 'V126' })`），**与路径无关** ⇒ 搬迁不可能改变该判定，42 处全属存量/已授权。
- **待裁项（本批不动）**：脚本 IR03 豁免名录只认 V126，未认上述授权例外。扩豁免属独立裁决，不为换绿而做。
- 另：D-15/D-16 两项 lint 自身假绿缺陷（DR04/DR05 用 `Singleline` 恒 0 命中、DR02 捕获组缺 `.` 只吃 schema 段）仍在册，故上表 DR02/DR04/DR05 的 PASS **须打折读**。
